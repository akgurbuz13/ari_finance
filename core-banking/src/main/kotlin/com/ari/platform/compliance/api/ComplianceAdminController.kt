package com.ari.platform.compliance.api

import com.ari.platform.compliance.internal.service.CaseManagementService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/compliance")
@PreAuthorize("hasRole('ADMIN')")
class ComplianceAdminController(
    private val caseManagementService: CaseManagementService,
    private val jdbcTemplate: JdbcTemplate
) {

    @GetMapping("/cases")
    fun listCases(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") pageSize: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "created_at") sortBy: String,
        @RequestParam(defaultValue = "desc") sortOrder: String
    ): ResponseEntity<CaseListResponse> {
        val clampedPage = page.coerceAtLeast(1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val offset = (clampedPage - 1) * clampedPageSize

        val allowedSortColumns = setOf("created_at", "updated_at", "status", "type")
        val safeSortBy = if (sortBy in allowedSortColumns) sortBy else "created_at"
        val safeSortOrder = if (sortOrder.lowercase() == "asc") "ASC" else "DESC"

        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!status.isNullOrBlank()) {
            conditions.add("cc.status = ?")
            params.add(status)
        }

        if (!type.isNullOrBlank()) {
            conditions.add("cc.type = ?")
            params.add(type)
        }

        if (!search.isNullOrBlank()) {
            conditions.add("(cc.description ILIKE ? OR u.email ILIKE ?)")
            val pattern = "%$search%"
            params.add(pattern)
            params.add(pattern)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")

        val countSql = """
            SELECT COUNT(*)
            FROM shared.compliance_cases cc
            JOIN identity.users u ON cc.user_id = u.id
            $whereClause
        """
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L

        val querySql = """
            SELECT cc.id, cc.type, cc.user_id, cc.status, cc.description,
                   cc.assigned_to, cc.created_at, cc.updated_at, cc.resolved_at, cc.resolution,
                   COALESCE(u.first_name || ' ' || u.last_name, u.email) as user_name
            FROM shared.compliance_cases cc
            JOIN identity.users u ON cc.user_id = u.id
            $whereClause
            ORDER BY cc.$safeSortBy $safeSortOrder
            LIMIT ? OFFSET ?
        """
        val queryParams = params.toMutableList()
        queryParams.add(clampedPageSize)
        queryParams.add(offset)

        val items = jdbcTemplate.query(querySql, { rs, _ ->
            ComplianceCaseResponse(
                id = rs.getString("id"),
                type = rs.getString("type"),
                userId = rs.getString("user_id"),
                userName = rs.getString("user_name") ?: "",
                description = rs.getString("description"),
                assignedTo = rs.getString("assigned_to") ?: "",
                status = rs.getString("status"),
                priority = derivePriority(rs.getString("type")),
                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
                resolvedAt = rs.getTimestamp("resolved_at")?.toInstant()?.toString(),
                resolution = rs.getString("resolution")
            )
        }, *queryParams.toTypedArray())

        return ResponseEntity.ok(
            CaseListResponse(
                items = items,
                total = total,
                page = clampedPage,
                pageSize = clampedPageSize
            )
        )
    }

    @PostMapping("/cases/{caseId}/resolve")
    fun resolveCase(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ResolveCaseRequest
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        caseManagementService.resolveCase(caseId, adminId, request.resolution)
        return ResponseEntity.ok().build()
    }

    // --- Sanctions List Admin Endpoints ---

    @GetMapping("/sanctions")
    fun listSanctions(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) listType: String?,
        @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<SanctionsListResponse> {
        val clampedPage = page.coerceAtLeast(1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val offset = (clampedPage - 1) * clampedPageSize

        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!search.isNullOrBlank()) {
            conditions.add("full_name ILIKE ?")
            params.add("%$search%")
        }

        if (!listType.isNullOrBlank()) {
            conditions.add("list_type = ?")
            params.add(listType)
        }

        if (active != null) {
            conditions.add("active = ?")
            params.add(active)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")

        val countSql = "SELECT COUNT(*) FROM shared.sanctions_list $whereClause"
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L

        val querySql = """
            SELECT id, full_name, list_type, source, country, aliases, active, added_at, updated_at
            FROM shared.sanctions_list
            $whereClause
            ORDER BY added_at DESC
            LIMIT ? OFFSET ?
        """
        val queryParams = params.toMutableList()
        queryParams.add(clampedPageSize)
        queryParams.add(offset)

        val items = jdbcTemplate.query(querySql, { rs, _ ->
            val aliasArray = rs.getArray("aliases")
            val aliases: List<String> = if (aliasArray != null) {
                @Suppress("UNCHECKED_CAST")
                (aliasArray.array as Array<String>).toList()
            } else {
                emptyList()
            }
            SanctionsEntryResponse(
                id = rs.getLong("id"),
                fullName = rs.getString("full_name"),
                listType = rs.getString("list_type"),
                source = rs.getString("source"),
                country = rs.getString("country"),
                aliases = aliases,
                active = rs.getBoolean("active"),
                addedAt = rs.getTimestamp("added_at").toInstant().toString(),
                updatedAt = rs.getTimestamp("updated_at").toInstant().toString()
            )
        }, *queryParams.toTypedArray())

        return ResponseEntity.ok(
            SanctionsListResponse(
                items = items,
                total = total,
                page = clampedPage,
                pageSize = clampedPageSize
            )
        )
    }

    @PostMapping("/sanctions")
    fun addSanctionsEntry(
        @Valid @RequestBody request: CreateSanctionsEntryRequest
    ): ResponseEntity<SanctionsEntryResponse> {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO shared.sanctions_list (full_name, list_type, source, country, aliases)
            VALUES (?, ?, ?, ?, ?::text[])
            RETURNING id
            """,
            Long::class.java,
            request.fullName,
            request.listType,
            request.source,
            request.country,
            request.aliases?.let { "{${it.joinToString(",") { alias -> "\"$alias\"" }}}" }
        )!!

        val entry = jdbcTemplate.queryForObject(
            """
            SELECT id, full_name, list_type, source, country, aliases, active, added_at, updated_at
            FROM shared.sanctions_list WHERE id = ?
            """,
            { rs, _ ->
                val aliasArray = rs.getArray("aliases")
                val aliases: List<String> = if (aliasArray != null) {
                    @Suppress("UNCHECKED_CAST")
                    (aliasArray.array as Array<String>).toList()
                } else {
                    emptyList()
                }
                SanctionsEntryResponse(
                    id = rs.getLong("id"),
                    fullName = rs.getString("full_name"),
                    listType = rs.getString("list_type"),
                    source = rs.getString("source"),
                    country = rs.getString("country"),
                    aliases = aliases,
                    active = rs.getBoolean("active"),
                    addedAt = rs.getTimestamp("added_at").toInstant().toString(),
                    updatedAt = rs.getTimestamp("updated_at").toInstant().toString()
                )
            },
            id
        )!!

        return ResponseEntity.ok(entry)
    }

    @DeleteMapping("/sanctions/{id}")
    fun deactivateSanctionsEntry(@PathVariable id: Long): ResponseEntity<Void> {
        jdbcTemplate.update(
            "UPDATE shared.sanctions_list SET active = false, updated_at = now() WHERE id = ?",
            id
        )
        return ResponseEntity.ok().build()
    }

    private fun derivePriority(type: String): String {
        return when (type) {
            "sanctions_hit" -> "critical"
            "pep_match" -> "high"
            "suspicious_activity" -> "high"
            "velocity_breach" -> "medium"
            "manual_review" -> "low"
            else -> "medium"
        }
    }
}

data class ResolveCaseRequest(
    @field:NotBlank val resolution: String
)

data class ComplianceCaseResponse(
    val id: String,
    val type: String,
    val userId: String,
    val userName: String,
    val description: String,
    val assignedTo: String,
    val status: String,
    val priority: String,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String?,
    val resolution: String?
)

data class CaseListResponse(
    val items: List<ComplianceCaseResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

data class CreateSanctionsEntryRequest(
    @field:NotBlank val fullName: String,
    @field:NotBlank val listType: String,
    @field:NotBlank val source: String,
    val country: String? = null,
    val aliases: List<String>? = null
)

data class SanctionsEntryResponse(
    val id: Long,
    val fullName: String,
    val listType: String,
    val source: String,
    val country: String?,
    val aliases: List<String>,
    val active: Boolean,
    val addedAt: String,
    val updatedAt: String
)

data class SanctionsListResponse(
    val items: List<SanctionsEntryResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

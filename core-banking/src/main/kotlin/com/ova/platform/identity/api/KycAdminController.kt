package com.ova.platform.identity.api

import com.ova.platform.identity.internal.service.KycService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/kyc")
@PreAuthorize("hasRole('ADMIN')")
class KycAdminController(
    private val kycService: KycService,
    private val jdbcTemplate: JdbcTemplate
) {

    @GetMapping("/verifications")
    fun listVerifications(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") pageSize: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "created_at") sortBy: String,
        @RequestParam(defaultValue = "desc") sortOrder: String
    ): ResponseEntity<KycListResponse> {
        val clampedPage = page.coerceAtLeast(1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val offset = (clampedPage - 1) * clampedPageSize

        val allowedSortColumns = setOf("created_at", "status", "provider", "level")
        val safeSortBy = if (sortBy in allowedSortColumns) sortBy else "created_at"
        val safeSortOrder = if (sortOrder.lowercase() == "asc") "ASC" else "DESC"

        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!status.isNullOrBlank()) {
            conditions.add("k.status = ?")
            params.add(status)
        }

        if (!search.isNullOrBlank()) {
            conditions.add("(u.email ILIKE ? OR u.first_name ILIKE ? OR u.last_name ILIKE ?)")
            val pattern = "%$search%"
            params.add(pattern)
            params.add(pattern)
            params.add(pattern)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")

        val countSql = """
            SELECT COUNT(*)
            FROM identity.kyc_verifications k
            JOIN identity.users u ON k.user_id = u.id
            $whereClause
        """
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L

        val querySql = """
            SELECT k.id, k.user_id, k.provider, k.status, k.level, k.created_at,
                   k.decision_at, k.decision_by, k.rejection_reason,
                   u.email,
                   COALESCE(u.first_name || ' ' || u.last_name, u.email) as user_name,
                   u.region
            FROM identity.kyc_verifications k
            JOIN identity.users u ON k.user_id = u.id
            $whereClause
            ORDER BY k.$safeSortBy $safeSortOrder
            LIMIT ? OFFSET ?
        """
        val queryParams = params.toMutableList()
        queryParams.add(clampedPageSize)
        queryParams.add(offset)

        val items = jdbcTemplate.query(querySql, { rs, _ ->
            KycVerificationResponse(
                id = rs.getString("id"),
                userId = rs.getString("user_id"),
                userName = rs.getString("user_name") ?: "",
                email = rs.getString("email"),
                status = rs.getString("status"),
                provider = rs.getString("provider"),
                region = rs.getString("region"),
                submittedAt = rs.getTimestamp("created_at").toInstant().toString(),
                reviewedAt = rs.getTimestamp("decision_at")?.toInstant()?.toString(),
                reviewedBy = rs.getString("decision_by"),
                rejectionReason = rs.getString("rejection_reason")
            )
        }, *queryParams.toTypedArray())

        return ResponseEntity.ok(
            KycListResponse(
                items = items,
                total = total,
                page = clampedPage,
                pageSize = clampedPageSize
            )
        )
    }

    @PostMapping("/verifications/{verificationId}/approve")
    fun approveKyc(@PathVariable verificationId: UUID): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        kycService.approveKyc(verificationId, adminId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/verifications/{verificationId}/reject")
    fun rejectKyc(
        @PathVariable verificationId: UUID,
        @Valid @RequestBody request: AdminRejectKycRequest
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        kycService.rejectKyc(verificationId, adminId, request.reason)
        return ResponseEntity.ok().build()
    }
}

data class AdminRejectKycRequest(
    @field:NotBlank val reason: String
)

data class KycVerificationResponse(
    val id: String,
    val userId: String,
    val userName: String,
    val email: String,
    val status: String,
    val provider: String,
    val region: String,
    val submittedAt: String,
    val reviewedAt: String?,
    val reviewedBy: String?,
    val rejectionReason: String?
)

data class KycListResponse(
    val items: List<KycVerificationResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

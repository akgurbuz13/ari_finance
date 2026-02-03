package com.ova.platform.compliance.internal.service

import com.ova.platform.shared.exception.NotFoundException
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class CaseManagementService(
    private val jdbcTemplate: JdbcTemplate,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ComplianceCase(
        val id: UUID = UUID.randomUUID(),
        val userId: UUID,
        val type: String,
        val status: String = "open",
        val description: String,
        val assignedTo: UUID? = null,
        val resolution: String? = null,
        val createdAt: Instant = Instant.now(),
        val updatedAt: Instant = Instant.now(),
        val resolvedAt: Instant? = null
    )

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        ComplianceCase(
            id = UUID.fromString(rs.getString("id")),
            userId = UUID.fromString(rs.getString("user_id")),
            type = rs.getString("type"),
            status = rs.getString("status"),
            description = rs.getString("description"),
            assignedTo = rs.getString("assigned_to")?.let { UUID.fromString(it) },
            resolution = rs.getString("resolution"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            resolvedAt = rs.getTimestamp("resolved_at")?.toInstant()
        )
    }

    @Transactional
    fun createCase(userId: UUID, type: String, description: String): ComplianceCase {
        val caseId = UUID.randomUUID()
        val now = Instant.now()

        jdbcTemplate.update(
            """
            INSERT INTO shared.compliance_cases (id, user_id, type, status, description, created_at, updated_at)
            VALUES (?, ?, ?, 'open', ?, ?, ?)
            """,
            caseId, userId, type, description, Timestamp.from(now), Timestamp.from(now)
        )

        log.info("Created compliance case id={} type={} for user={}", caseId, type, userId)

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "create_compliance_case",
            resourceType = "compliance_case",
            resourceId = caseId.toString(),
            details = mapOf("userId" to userId.toString(), "type" to type)
        )

        return ComplianceCase(
            id = caseId,
            userId = userId,
            type = type,
            status = "open",
            description = description,
            createdAt = now,
            updatedAt = now
        )
    }

    @Transactional
    fun resolveCase(caseId: UUID, adminId: UUID, resolution: String) {
        val now = Instant.now()

        val rowsUpdated = jdbcTemplate.update(
            """
            UPDATE shared.compliance_cases
            SET status = 'resolved',
                resolution = ?,
                assigned_to = ?,
                updated_at = ?,
                resolved_at = ?
            WHERE id = ?
              AND status IN ('open', 'under_review')
            """,
            resolution, adminId, Timestamp.from(now), Timestamp.from(now), caseId
        )

        if (rowsUpdated == 0) {
            throw NotFoundException("compliance_case", caseId.toString())
        }

        log.info("Resolved compliance case id={} by admin={}", caseId, adminId)

        auditService.log(
            actorId = adminId,
            actorType = "admin",
            action = "resolve_compliance_case",
            resourceType = "compliance_case",
            resourceId = caseId.toString(),
            details = mapOf("resolution" to resolution)
        )
    }

    fun findOpenCases(limit: Int = 50, offset: Int = 0): List<ComplianceCase> {
        return jdbcTemplate.query(
            """
            SELECT id, user_id, type, status, description, assigned_to, resolution, created_at, updated_at, resolved_at
            FROM shared.compliance_cases
            WHERE status IN ('open', 'under_review')
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """,
            rowMapper,
            limit, offset
        )
    }

    fun findByUserId(userId: UUID): List<ComplianceCase> {
        return jdbcTemplate.query(
            """
            SELECT id, user_id, type, status, description, assigned_to, resolution, created_at, updated_at, resolved_at
            FROM shared.compliance_cases
            WHERE user_id = ?
            ORDER BY created_at DESC
            """,
            rowMapper,
            userId
        )
    }

    fun findById(caseId: UUID): ComplianceCase? {
        val results = jdbcTemplate.query(
            """
            SELECT id, user_id, type, status, description, assigned_to, resolution, created_at, updated_at, resolved_at
            FROM shared.compliance_cases
            WHERE id = ?
            """,
            rowMapper,
            caseId
        )
        return results.firstOrNull()
    }
}

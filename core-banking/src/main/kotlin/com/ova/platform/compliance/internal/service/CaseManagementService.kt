package com.ova.platform.compliance.internal.service

import com.ova.platform.shared.security.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CaseManagementService(
    private val jdbcTemplate: JdbcTemplate,
    private val auditService: AuditService
) {

    data class ComplianceCase(
        val id: UUID = UUID.randomUUID(),
        val userId: UUID,
        val type: String,
        val status: String = "open",
        val description: String,
        val assignedTo: UUID? = null,
        val resolution: String? = null,
        val createdAt: Instant = Instant.now()
    )

    @Transactional
    fun createCase(userId: UUID, type: String, description: String): ComplianceCase {
        val case_ = ComplianceCase(
            userId = userId,
            type = type,
            description = description
        )

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "create_compliance_case",
            resourceType = "compliance_case",
            resourceId = case_.id.toString(),
            details = mapOf("userId" to userId.toString(), "type" to type)
        )

        return case_
    }

    fun resolveCase(caseId: UUID, adminId: UUID, resolution: String) {
        auditService.log(
            actorId = adminId,
            actorType = "admin",
            action = "resolve_compliance_case",
            resourceType = "compliance_case",
            resourceId = caseId.toString(),
            details = mapOf("resolution" to resolution)
        )
    }
}

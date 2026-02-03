package com.ova.platform.shared.security

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuditService(private val jdbcTemplate: JdbcTemplate) {

    fun log(
        actorId: UUID?,
        actorType: String,
        action: String,
        resourceType: String,
        resourceId: String,
        details: Map<String, Any>? = null,
        ipAddress: String? = null
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO shared.audit_log (actor_id, actor_type, action, resource_type, resource_id, details, ip_address)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::inet)
            """,
            actorId,
            actorType,
            action,
            resourceType,
            resourceId,
            details?.let { com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(it) },
            ipAddress
        )
    }
}

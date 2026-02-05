package com.ova.platform.payments.internal.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class RailReference(
    val id: Long? = null,
    val paymentOrderId: UUID,
    val provider: String,
    val externalReference: String,
    val status: String = "pending",
    val rawResponse: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

@Repository
class RailReferenceRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        RailReference(
            id = rs.getLong("id"),
            paymentOrderId = UUID.fromString(rs.getString("payment_order_id")),
            provider = rs.getString("provider"),
            externalReference = rs.getString("external_reference"),
            status = rs.getString("status"),
            rawResponse = rs.getString("raw_response"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun save(ref: RailReference): RailReference {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO payments.rail_references
                (payment_order_id, provider, external_reference, status, raw_response)
            VALUES (?, ?, ?, ?, ?::jsonb)
            RETURNING id
            """,
            Long::class.java,
            ref.paymentOrderId, ref.provider, ref.externalReference, ref.status, ref.rawResponse
        )!!
        return ref.copy(id = id)
    }

    fun findByExternalReference(provider: String, externalReference: String): RailReference? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.rail_references WHERE provider = ? AND external_reference = ?",
            rowMapper, provider, externalReference
        ).firstOrNull()
    }

    fun findByPaymentOrderId(paymentOrderId: UUID): List<RailReference> {
        return jdbcTemplate.query(
            "SELECT * FROM payments.rail_references WHERE payment_order_id = ? ORDER BY created_at DESC",
            rowMapper, paymentOrderId
        )
    }

    fun findPendingOrSubmitted(limit: Int = 100): List<RailReference> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.rail_references
            WHERE status IN ('pending', 'submitted')
            ORDER BY created_at ASC
            LIMIT ?
            """,
            rowMapper, limit
        )
    }

    fun updateStatus(id: Long, status: String, rawResponse: String? = null) {
        jdbcTemplate.update(
            """
            UPDATE payments.rail_references
            SET status = ?, raw_response = COALESCE(?::jsonb, raw_response), updated_at = now()
            WHERE id = ?
            """,
            status, rawResponse, id
        )
    }
}

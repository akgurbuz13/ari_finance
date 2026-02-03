package com.ova.platform.payments.internal.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.ova.platform.payments.internal.model.PaymentOrder
import com.ova.platform.payments.internal.model.PaymentStatus
import com.ova.platform.payments.internal.model.PaymentType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class PaymentOrderRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        PaymentOrder(
            id = UUID.fromString(rs.getString("id")),
            idempotencyKey = rs.getString("idempotency_key"),
            type = PaymentType.fromValue(rs.getString("type")),
            status = PaymentStatus.fromValue(rs.getString("status")),
            senderAccountId = UUID.fromString(rs.getString("sender_account_id")),
            receiverAccountId = UUID.fromString(rs.getString("receiver_account_id")),
            amount = rs.getBigDecimal("amount"),
            currency = rs.getString("currency"),
            feeAmount = rs.getBigDecimal("fee_amount"),
            feeCurrency = rs.getString("fee_currency"),
            fxQuoteId = rs.getString("fx_quote_id")?.let { UUID.fromString(it) },
            ledgerTransactionId = rs.getString("ledger_transaction_id")?.let { UUID.fromString(it) },
            description = rs.getString("description"),
            metadata = rs.getString("metadata")?.let {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(it, Map::class.java) as Map<String, Any>
            },
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant()
        )
    }

    fun save(order: PaymentOrder): PaymentOrder {
        jdbcTemplate.update(
            """
            INSERT INTO payments.payment_orders
                (id, idempotency_key, type, status, sender_account_id, receiver_account_id,
                 amount, currency, fee_amount, fee_currency, fx_quote_id,
                 ledger_transaction_id, description, metadata, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
            order.id,
            order.idempotencyKey,
            order.type.value,
            order.status.value,
            order.senderAccountId,
            order.receiverAccountId,
            order.amount,
            order.currency,
            order.feeAmount,
            order.feeCurrency,
            order.fxQuoteId,
            order.ledgerTransactionId,
            order.description,
            order.metadata?.let { objectMapper.writeValueAsString(it) },
            order.completedAt?.let { java.sql.Timestamp.from(it) }
        )
        return order
    }

    fun findById(id: UUID): PaymentOrder? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.payment_orders WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun findByIdempotencyKey(key: String): PaymentOrder? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.payment_orders WHERE idempotency_key = ?", rowMapper, key
        ).firstOrNull()
    }

    fun findByStatus(status: PaymentStatus, limit: Int = 50, offset: Int = 0): List<PaymentOrder> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.payment_orders
            WHERE status = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """,
            rowMapper, status.value, limit, offset
        )
    }

    fun findBySenderAccountId(senderAccountId: UUID, limit: Int = 50, offset: Int = 0): List<PaymentOrder> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.payment_orders
            WHERE sender_account_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """,
            rowMapper, senderAccountId, limit, offset
        )
    }

    fun findByAccountId(accountId: UUID, limit: Int = 50, offset: Int = 0): List<PaymentOrder> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.payment_orders
            WHERE sender_account_id = ? OR receiver_account_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """,
            rowMapper, accountId, accountId, limit, offset
        )
    }

    fun updateStatus(id: UUID, status: PaymentStatus) {
        val completedAt = if (status == PaymentStatus.COMPLETED) ", completed_at = now()" else ""
        jdbcTemplate.update(
            "UPDATE payments.payment_orders SET status = ?, updated_at = now()$completedAt WHERE id = ?",
            status.value, id
        )
    }

    fun updateLedgerTransactionId(id: UUID, ledgerTransactionId: UUID) {
        jdbcTemplate.update(
            "UPDATE payments.payment_orders SET ledger_transaction_id = ?, updated_at = now() WHERE id = ?",
            ledgerTransactionId, id
        )
    }

    fun delete(id: UUID) {
        jdbcTemplate.update(
            "DELETE FROM payments.payment_orders WHERE id = ?", id
        )
    }
}

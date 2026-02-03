package com.ova.platform.payments.internal.repository

import com.ova.platform.payments.internal.model.PaymentStatus
import com.ova.platform.payments.internal.model.PaymentStatusHistory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class PaymentStatusHistoryRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        PaymentStatusHistory(
            id = rs.getLong("id"),
            paymentOrderId = UUID.fromString(rs.getString("payment_order_id")),
            fromStatus = rs.getString("from_status")?.let { PaymentStatus.fromValue(it) },
            toStatus = PaymentStatus.fromValue(rs.getString("to_status")),
            reason = rs.getString("reason"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(history: PaymentStatusHistory): PaymentStatusHistory {
        jdbcTemplate.update(
            """
            INSERT INTO payments.payment_status_history
                (payment_order_id, from_status, to_status, reason)
            VALUES (?, ?, ?, ?)
            """,
            history.paymentOrderId,
            history.fromStatus?.value,
            history.toStatus.value,
            history.reason
        )
        return history
    }

    fun findByPaymentOrderId(paymentOrderId: UUID): List<PaymentStatusHistory> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.payment_status_history
            WHERE payment_order_id = ?
            ORDER BY created_at ASC
            """,
            rowMapper, paymentOrderId
        )
    }
}

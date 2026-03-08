package com.ari.platform.payments.internal.repository

import com.ari.platform.payments.internal.model.EscrowState
import com.ari.platform.payments.internal.model.VehicleEscrow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class VehicleEscrowRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        VehicleEscrow(
            id = UUID.fromString(rs.getString("id")),
            onChainEscrowId = rs.getLong("on_chain_escrow_id").takeIf { !rs.wasNull() },
            vehicleRegistrationId = UUID.fromString(rs.getString("vehicle_registration_id")),
            sellerUserId = UUID.fromString(rs.getString("seller_user_id")),
            buyerUserId = rs.getString("buyer_user_id")?.let { UUID.fromString(it) },
            saleAmount = rs.getBigDecimal("sale_amount"),
            feeAmount = rs.getBigDecimal("fee_amount"),
            currency = rs.getString("currency"),
            state = EscrowState.fromValue(rs.getString("state")),
            sellerConfirmed = rs.getBoolean("seller_confirmed"),
            buyerConfirmed = rs.getBoolean("buyer_confirmed"),
            shareCode = rs.getString("share_code"),
            setupTxHash = rs.getString("setup_tx_hash"),
            fundTxHash = rs.getString("fund_tx_hash"),
            completeTxHash = rs.getString("complete_tx_hash"),
            cancelTxHash = rs.getString("cancel_tx_hash"),
            paymentOrderId = rs.getString("payment_order_id")?.let { UUID.fromString(it) },
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant()
        )
    }

    fun save(escrow: VehicleEscrow): VehicleEscrow {
        jdbcTemplate.update(
            """
            INSERT INTO payments.vehicle_escrows
                (id, on_chain_escrow_id, vehicle_registration_id, seller_user_id, buyer_user_id,
                 sale_amount, fee_amount, currency, state, seller_confirmed, buyer_confirmed,
                 share_code, setup_tx_hash, fund_tx_hash, complete_tx_hash, cancel_tx_hash,
                 payment_order_id, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            escrow.id, escrow.onChainEscrowId, escrow.vehicleRegistrationId,
            escrow.sellerUserId, escrow.buyerUserId,
            escrow.saleAmount, escrow.feeAmount, escrow.currency,
            escrow.state.value, escrow.sellerConfirmed, escrow.buyerConfirmed,
            escrow.shareCode, escrow.setupTxHash, escrow.fundTxHash,
            escrow.completeTxHash, escrow.cancelTxHash,
            escrow.paymentOrderId,
            escrow.completedAt?.let { java.sql.Timestamp.from(it) }
        )
        return escrow
    }

    fun findById(id: UUID): VehicleEscrow? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_escrows WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun findByShareCode(shareCode: String): VehicleEscrow? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_escrows WHERE share_code = ?", rowMapper, shareCode
        ).firstOrNull()
    }

    fun findBySellerOrBuyer(userId: UUID): List<VehicleEscrow> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.vehicle_escrows
            WHERE seller_user_id = ? OR buyer_user_id = ?
            ORDER BY created_at DESC
            """,
            rowMapper, userId, userId
        )
    }

    fun findByVehicleRegistrationId(vehicleRegistrationId: UUID): List<VehicleEscrow> {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_escrows WHERE vehicle_registration_id = ? ORDER BY created_at DESC",
            rowMapper, vehicleRegistrationId
        )
    }

    fun updateState(id: UUID, state: EscrowState) {
        val completedAt = if (state == EscrowState.COMPLETED) ", completed_at = now()" else ""
        jdbcTemplate.update(
            "UPDATE payments.vehicle_escrows SET state = ?, updated_at = now()$completedAt WHERE id = ?",
            state.value, id
        )
    }

    fun updateBuyer(id: UUID, buyerUserId: UUID) {
        jdbcTemplate.update(
            "UPDATE payments.vehicle_escrows SET buyer_user_id = ?, updated_at = now() WHERE id = ?",
            buyerUserId, id
        )
    }

    fun updateOnChainEscrowId(id: UUID, onChainEscrowId: Long, setupTxHash: String) {
        jdbcTemplate.update(
            """
            UPDATE payments.vehicle_escrows
            SET on_chain_escrow_id = ?, setup_tx_hash = ?, state = ?, updated_at = now()
            WHERE id = ?
            """,
            onChainEscrowId, setupTxHash, EscrowState.SETUP_COMPLETE.value, id
        )
    }

    fun updateFundTxHash(id: UUID, fundTxHash: String) {
        jdbcTemplate.update(
            "UPDATE payments.vehicle_escrows SET fund_tx_hash = ?, state = ?, updated_at = now() WHERE id = ?",
            fundTxHash, EscrowState.FUNDED.value, id
        )
    }

    fun updateConfirmation(id: UUID, role: String, confirmed: Boolean) {
        val column = if (role == "SELLER") "seller_confirmed" else "buyer_confirmed"
        val state = if (role == "SELLER") EscrowState.SELLER_CONFIRMED.value else EscrowState.BUYER_CONFIRMED.value
        jdbcTemplate.update(
            "UPDATE payments.vehicle_escrows SET $column = ?, state = ?, updated_at = now() WHERE id = ?",
            confirmed, state, id
        )
    }

    fun updateCompleteTxHash(id: UUID, completeTxHash: String) {
        jdbcTemplate.update(
            """
            UPDATE payments.vehicle_escrows
            SET complete_tx_hash = ?, state = ?, updated_at = now(), completed_at = now()
            WHERE id = ?
            """,
            completeTxHash, EscrowState.COMPLETED.value, id
        )
    }

    fun updateCancelTxHash(id: UUID, cancelTxHash: String) {
        jdbcTemplate.update(
            "UPDATE payments.vehicle_escrows SET cancel_tx_hash = ?, state = ?, updated_at = now() WHERE id = ?",
            cancelTxHash, EscrowState.CANCELLED.value, id
        )
    }
}

package com.ova.blockchain.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class BlockchainTransaction(
    val id: Long? = null,
    val txHash: String,
    val chainId: Long,
    val operation: String,
    val fromAddress: String? = null,
    val toAddress: String? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    val currency: String,
    val status: String = "pending",
    val blockNumber: Long? = null,
    val gasUsed: Long? = null,
    val paymentOrderId: UUID? = null,
    val errorMessage: String? = null,
    val metadata: String? = null,
    val createdAt: Instant = Instant.now(),
    val confirmedAt: Instant? = null
)

@Repository
class BlockchainTransactionRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        BlockchainTransaction(
            id = rs.getLong("id"),
            txHash = rs.getString("tx_hash"),
            chainId = rs.getLong("chain_id"),
            operation = rs.getString("operation"),
            fromAddress = rs.getString("from_address"),
            toAddress = rs.getString("to_address"),
            amount = rs.getBigDecimal("amount"),
            currency = rs.getString("currency"),
            status = rs.getString("status"),
            blockNumber = rs.getLong("block_number").takeIf { !rs.wasNull() },
            gasUsed = rs.getLong("gas_used").takeIf { !rs.wasNull() },
            paymentOrderId = rs.getString("payment_order_id")?.let { UUID.fromString(it) },
            errorMessage = rs.getString("error_message"),
            metadata = rs.getString("metadata"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            confirmedAt = rs.getTimestamp("confirmed_at")?.toInstant()
        )
    }

    fun save(tx: BlockchainTransaction): BlockchainTransaction {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO blockchain.transactions
                (tx_hash, chain_id, operation, from_address, to_address, amount, currency,
                 status, block_number, gas_used, payment_order_id, error_message, metadata, confirmed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (chain_id, tx_hash) DO UPDATE SET
                status = EXCLUDED.status,
                block_number = EXCLUDED.block_number,
                gas_used = EXCLUDED.gas_used,
                error_message = EXCLUDED.error_message,
                metadata = COALESCE(EXCLUDED.metadata, blockchain.transactions.metadata),
                confirmed_at = EXCLUDED.confirmed_at
            RETURNING id
            """,
            Long::class.java,
            tx.txHash, tx.chainId, tx.operation, tx.fromAddress, tx.toAddress,
            tx.amount, tx.currency, tx.status, tx.blockNumber, tx.gasUsed,
            tx.paymentOrderId, tx.errorMessage, tx.metadata,
            tx.confirmedAt?.let { java.sql.Timestamp.from(it) }
        )
        return tx.copy(id = id)
    }

    fun updateStatus(id: Long, status: String, blockNumber: Long? = null, gasUsed: Long? = null) {
        jdbcTemplate.update(
            """
            UPDATE blockchain.transactions
            SET status = ?, block_number = COALESCE(?, block_number),
                gas_used = COALESCE(?, gas_used),
                confirmed_at = CASE WHEN ? = 'confirmed' THEN now() ELSE confirmed_at END
            WHERE id = ?
            """,
            status, blockNumber, gasUsed, status, id
        )
    }

    fun findByTxHash(chainId: Long, txHash: String): BlockchainTransaction? {
        return jdbcTemplate.query(
            "SELECT * FROM blockchain.transactions WHERE chain_id = ? AND tx_hash = ?",
            rowMapper, chainId, txHash
        ).firstOrNull()
    }

    fun findByPaymentOrderId(paymentOrderId: UUID): List<BlockchainTransaction> {
        return jdbcTemplate.query(
            "SELECT * FROM blockchain.transactions WHERE payment_order_id = ? ORDER BY created_at",
            rowMapper, paymentOrderId
        )
    }

    fun findPending(limit: Int = 50): List<BlockchainTransaction> {
        return jdbcTemplate.query(
            """
            SELECT * FROM blockchain.transactions
            WHERE status IN ('pending', 'submitted')
            ORDER BY created_at ASC
            LIMIT ?
            """,
            rowMapper, limit
        )
    }

    /**
     * Find transactions by transfer ID stored in metadata.
     * Used for tracking cross-chain bridge transfers.
     */
    fun findByTransferId(transferId: String): List<BlockchainTransaction> {
        return jdbcTemplate.query(
            """
            SELECT * FROM blockchain.transactions
            WHERE metadata IS NOT NULL
              AND metadata ->> 'transferId' = ?
            ORDER BY created_at ASC
            """,
            rowMapper, transferId
        )
    }

    /**
     * Find pending bridge transfers on a specific chain.
     * Used for monitoring and completing cross-chain operations.
     */
    fun findPendingBridgeTransfers(chainId: Long): List<BlockchainTransaction> {
        return jdbcTemplate.query(
            """
            SELECT * FROM blockchain.transactions
            WHERE chain_id = ?
              AND operation IN ('bridge_initiate', 'bridge_back')
              AND status IN ('pending', 'submitted', 'pending_relay')
            ORDER BY created_at ASC
            """,
            rowMapper, chainId
        )
    }
}

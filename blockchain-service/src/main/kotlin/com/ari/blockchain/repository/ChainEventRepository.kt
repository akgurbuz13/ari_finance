package com.ari.blockchain.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant

data class ChainEvent(
    val id: Long? = null,
    val chainId: Long,
    val blockNumber: Long,
    val txHash: String,
    val logIndex: Int,
    val eventType: String,
    val contractAddress: String,
    val fromAddress: String? = null,
    val toAddress: String? = null,
    val amount: BigDecimal? = null,
    val rawData: String? = null,
    val processed: Boolean = false,
    val processedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

@Repository
class ChainEventRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        ChainEvent(
            id = rs.getLong("id"),
            chainId = rs.getLong("chain_id"),
            blockNumber = rs.getLong("block_number"),
            txHash = rs.getString("tx_hash"),
            logIndex = rs.getInt("log_index"),
            eventType = rs.getString("event_type"),
            contractAddress = rs.getString("contract_address"),
            fromAddress = rs.getString("from_address"),
            toAddress = rs.getString("to_address"),
            amount = rs.getBigDecimal("amount"),
            rawData = rs.getString("raw_data"),
            processed = rs.getBoolean("processed"),
            processedAt = rs.getTimestamp("processed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(event: ChainEvent): Boolean {
        val rows = jdbcTemplate.update(
            """
            INSERT INTO blockchain.chain_events
                (chain_id, block_number, tx_hash, log_index, event_type, contract_address,
                 from_address, to_address, amount, raw_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (chain_id, tx_hash, log_index) DO NOTHING
            """,
            event.chainId, event.blockNumber, event.txHash, event.logIndex,
            event.eventType, event.contractAddress, event.fromAddress,
            event.toAddress, event.amount, event.rawData
        )
        return rows > 0
    }

    fun markProcessed(id: Long) {
        jdbcTemplate.update(
            "UPDATE blockchain.chain_events SET processed = true, processed_at = now() WHERE id = ?",
            id
        )
    }

    fun getLastProcessedBlock(chainId: Long): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(last_block, 0) FROM blockchain.block_cursors WHERE chain_id = ?",
            Long::class.java, chainId
        ) ?: 0L
    }

    fun updateLastProcessedBlock(chainId: Long, blockNumber: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO blockchain.block_cursors (chain_id, last_block, updated_at)
            VALUES (?, ?, now())
            ON CONFLICT (chain_id) DO UPDATE SET last_block = ?, updated_at = now()
            """,
            chainId, blockNumber, blockNumber
        )
    }
}

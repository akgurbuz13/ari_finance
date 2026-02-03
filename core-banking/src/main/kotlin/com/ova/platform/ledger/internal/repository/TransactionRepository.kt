package com.ova.platform.ledger.internal.repository

import com.ova.platform.ledger.internal.model.Transaction
import com.ova.platform.ledger.internal.model.TransactionStatus
import com.ova.platform.ledger.internal.model.TransactionType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class TransactionRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Transaction(
            id = UUID.fromString(rs.getString("id")),
            idempotencyKey = rs.getString("idempotency_key"),
            type = TransactionType.fromValue(rs.getString("type")),
            status = TransactionStatus.fromValue(rs.getString("status")),
            referenceId = rs.getString("reference_id"),
            metadata = rs.getString("metadata")?.let {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(it, Map::class.java) as Map<String, Any>
            },
            createdAt = rs.getTimestamp("created_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant()
        )
    }

    fun save(transaction: Transaction): Transaction {
        jdbcTemplate.update(
            """
            INSERT INTO ledger.transactions (id, idempotency_key, type, status, reference_id, metadata, completed_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
            transaction.id, transaction.idempotencyKey, transaction.type.value,
            transaction.status.value, transaction.referenceId,
            transaction.metadata?.let { objectMapper.writeValueAsString(it) },
            transaction.completedAt?.let { java.sql.Timestamp.from(it) }
        )
        return transaction
    }

    fun findById(id: UUID): Transaction? {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.transactions WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun findByIdempotencyKey(key: String): Transaction? {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.transactions WHERE idempotency_key = ?", rowMapper, key
        ).firstOrNull()
    }

    fun updateStatus(id: UUID, status: TransactionStatus) {
        val completedAt = if (status == TransactionStatus.COMPLETED) "now()" else "NULL"
        jdbcTemplate.update(
            "UPDATE ledger.transactions SET status = ?, completed_at = $completedAt WHERE id = ?",
            status.value, id
        )
    }

    fun findByAccountId(accountId: UUID, limit: Int = 50, offset: Int = 0): List<Transaction> {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT t.* FROM ledger.transactions t
            JOIN ledger.entries e ON e.transaction_id = t.id
            WHERE e.account_id = ?
            ORDER BY t.created_at DESC
            LIMIT ? OFFSET ?
            """,
            rowMapper, accountId, limit, offset
        )
    }

    fun findByAccountIdAndFilters(
        accountId: UUID,
        type: String?,
        from: Instant?,
        to: Instant?,
        limit: Int,
        offset: Int
    ): List<Transaction> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions.add("e.account_id = ?")
        params.add(accountId)

        if (type != null) {
            conditions.add("t.type = ?")
            params.add(type)
        }
        if (from != null) {
            conditions.add("t.created_at >= ?")
            params.add(Timestamp.from(from))
        }
        if (to != null) {
            conditions.add("t.created_at <= ?")
            params.add(Timestamp.from(to))
        }

        val whereClause = conditions.joinToString(" AND ")

        params.add(limit)
        params.add(offset)

        return jdbcTemplate.query(
            """
            SELECT DISTINCT t.* FROM ledger.transactions t
            JOIN ledger.entries e ON e.transaction_id = t.id
            WHERE $whereClause
            ORDER BY t.created_at DESC
            LIMIT ? OFFSET ?
            """,
            rowMapper,
            *params.toTypedArray()
        )
    }

    fun countByAccountIdAndFilters(
        accountId: UUID,
        type: String?,
        from: Instant?,
        to: Instant?
    ): Long {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions.add("e.account_id = ?")
        params.add(accountId)

        if (type != null) {
            conditions.add("t.type = ?")
            params.add(type)
        }
        if (from != null) {
            conditions.add("t.created_at >= ?")
            params.add(Timestamp.from(from))
        }
        if (to != null) {
            conditions.add("t.created_at <= ?")
            params.add(Timestamp.from(to))
        }

        val whereClause = conditions.joinToString(" AND ")

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT t.id) FROM ledger.transactions t
            JOIN ledger.entries e ON e.transaction_id = t.id
            WHERE $whereClause
            """,
            Long::class.java,
            *params.toTypedArray()
        ) ?: 0L
    }
}

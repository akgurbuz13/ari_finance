package com.ari.platform.ledger.internal.repository

import com.ari.platform.ledger.internal.model.Entry
import com.ari.platform.ledger.internal.model.EntryDirection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class EntryRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        Entry(
            id = rs.getLong("id"),
            transactionId = UUID.fromString(rs.getString("transaction_id")),
            accountId = UUID.fromString(rs.getString("account_id")),
            direction = EntryDirection.fromValue(rs.getString("direction")),
            amount = rs.getBigDecimal("amount"),
            currency = rs.getString("currency"),
            balanceAfter = rs.getBigDecimal("balance_after"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(entry: Entry): Entry {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger.entries (transaction_id, account_id, direction, amount, currency, balance_after)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long::class.java,
            entry.transactionId, entry.accountId, entry.direction.value,
            entry.amount, entry.currency, entry.balanceAfter
        )
        return entry.copy(id = id)
    }

    fun findByTransactionId(transactionId: UUID): List<Entry> {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.entries WHERE transaction_id = ? ORDER BY id",
            rowMapper, transactionId
        )
    }

    fun findByAccountId(accountId: UUID, limit: Int = 50, offset: Int = 0): List<Entry> {
        return jdbcTemplate.query(
            "SELECT * FROM ledger.entries WHERE account_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
            rowMapper, accountId, limit, offset
        )
    }

    fun getLatestBalance(accountId: UUID): BigDecimal {
        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                (SELECT balance_after FROM ledger.entries WHERE account_id = ? ORDER BY created_at DESC, id DESC LIMIT 1),
                0
            )
            """,
            BigDecimal::class.java, accountId
        ) ?: BigDecimal.ZERO
    }

    fun getBalanceAt(accountId: UUID, at: Instant): BigDecimal {
        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                (SELECT balance_after FROM ledger.entries
                 WHERE account_id = ? AND created_at <= ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1),
                0
            )
            """,
            BigDecimal::class.java,
            accountId,
            java.sql.Timestamp.from(at)
        ) ?: BigDecimal.ZERO
    }
}

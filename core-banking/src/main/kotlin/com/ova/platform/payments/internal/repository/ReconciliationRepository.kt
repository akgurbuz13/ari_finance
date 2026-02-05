package com.ova.platform.payments.internal.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RailReconciliation(
    val id: Long? = null,
    val railProvider: String,
    val reconciliationDate: LocalDate,
    val expectedAmount: BigDecimal,
    val actualAmount: BigDecimal,
    val currency: String,
    val discrepancy: BigDecimal = BigDecimal.ZERO,
    val matchedCount: Int = 0,
    val unmatchedCount: Int = 0,
    val status: String = "pending",
    val notes: String? = null,
    val resolvedBy: UUID? = null,
    val resolvedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

data class SafeguardingBalance(
    val id: Long? = null,
    val currency: String,
    val region: String,
    val bankName: String,
    val bankAccount: String,
    val ledgerBalance: BigDecimal,
    val bankBalance: BigDecimal,
    val discrepancy: BigDecimal = BigDecimal.ZERO,
    val asOfDate: LocalDate,
    val reconciled: Boolean = false,
    val createdAt: Instant = Instant.now()
)

@Repository
class ReconciliationRepository(private val jdbcTemplate: JdbcTemplate) {

    private val reconMapper = RowMapper { rs: ResultSet, _: Int ->
        RailReconciliation(
            id = rs.getLong("id"),
            railProvider = rs.getString("rail_provider"),
            reconciliationDate = rs.getDate("reconciliation_date").toLocalDate(),
            expectedAmount = rs.getBigDecimal("expected_amount"),
            actualAmount = rs.getBigDecimal("actual_amount"),
            currency = rs.getString("currency"),
            discrepancy = rs.getBigDecimal("discrepancy"),
            matchedCount = rs.getInt("matched_count"),
            unmatchedCount = rs.getInt("unmatched_count"),
            status = rs.getString("status"),
            notes = rs.getString("notes"),
            resolvedBy = rs.getString("resolved_by")?.let { UUID.fromString(it) },
            resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val safeguardMapper = RowMapper { rs: ResultSet, _: Int ->
        SafeguardingBalance(
            id = rs.getLong("id"),
            currency = rs.getString("currency"),
            region = rs.getString("region"),
            bankName = rs.getString("bank_name"),
            bankAccount = rs.getString("bank_account"),
            ledgerBalance = rs.getBigDecimal("ledger_balance"),
            bankBalance = rs.getBigDecimal("bank_balance"),
            discrepancy = rs.getBigDecimal("discrepancy"),
            asOfDate = rs.getDate("as_of_date").toLocalDate(),
            reconciled = rs.getBoolean("reconciled"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    // ── Rail Reconciliation ─────────────────────────────────────────────────

    fun saveReconciliation(recon: RailReconciliation): RailReconciliation {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO payments.rail_reconciliations
                (rail_provider, reconciliation_date, expected_amount, actual_amount, currency,
                 discrepancy, matched_count, unmatched_count, status, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (rail_provider, reconciliation_date, currency)
            DO UPDATE SET
                expected_amount = EXCLUDED.expected_amount,
                actual_amount = EXCLUDED.actual_amount,
                discrepancy = EXCLUDED.discrepancy,
                matched_count = EXCLUDED.matched_count,
                unmatched_count = EXCLUDED.unmatched_count,
                status = EXCLUDED.status,
                notes = EXCLUDED.notes
            RETURNING id
            """,
            Long::class.java,
            recon.railProvider, java.sql.Date.valueOf(recon.reconciliationDate),
            recon.expectedAmount, recon.actualAmount, recon.currency,
            recon.discrepancy, recon.matchedCount, recon.unmatchedCount,
            recon.status, recon.notes
        )!!
        return recon.copy(id = id)
    }

    fun findReconciliationsByDate(date: LocalDate): List<RailReconciliation> {
        return jdbcTemplate.query(
            "SELECT * FROM payments.rail_reconciliations WHERE reconciliation_date = ? ORDER BY rail_provider",
            reconMapper, java.sql.Date.valueOf(date)
        )
    }

    fun findDiscrepancies(limit: Int = 50): List<RailReconciliation> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.rail_reconciliations
            WHERE status IN ('pending', 'discrepancy')
            ORDER BY reconciliation_date DESC
            LIMIT ?
            """,
            reconMapper, limit
        )
    }

    fun resolveReconciliation(id: Long, resolvedBy: UUID, notes: String?) {
        jdbcTemplate.update(
            """
            UPDATE payments.rail_reconciliations
            SET status = 'resolved', resolved_by = ?, resolved_at = now(), notes = COALESCE(?, notes)
            WHERE id = ?
            """,
            resolvedBy, notes, id
        )
    }

    // ── Safeguarding Balances ───────────────────────────────────────────────

    fun saveSafeguardingBalance(balance: SafeguardingBalance): SafeguardingBalance {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO payments.safeguarding_balances
                (currency, region, bank_name, bank_account, ledger_balance, bank_balance,
                 discrepancy, as_of_date, reconciled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (currency, region, as_of_date)
            DO UPDATE SET
                ledger_balance = EXCLUDED.ledger_balance,
                bank_balance = EXCLUDED.bank_balance,
                discrepancy = EXCLUDED.discrepancy,
                reconciled = EXCLUDED.reconciled
            RETURNING id
            """,
            Long::class.java,
            balance.currency, balance.region, balance.bankName, balance.bankAccount,
            balance.ledgerBalance, balance.bankBalance, balance.discrepancy,
            java.sql.Date.valueOf(balance.asOfDate), balance.reconciled
        )!!
        return balance.copy(id = id)
    }

    fun findSafeguardingBalancesByDate(date: LocalDate): List<SafeguardingBalance> {
        return jdbcTemplate.query(
            "SELECT * FROM payments.safeguarding_balances WHERE as_of_date = ? ORDER BY currency, region",
            safeguardMapper, java.sql.Date.valueOf(date)
        )
    }

    fun findUnreconciledSafeguardingBalances(): List<SafeguardingBalance> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.safeguarding_balances
            WHERE NOT reconciled
            ORDER BY as_of_date DESC
            LIMIT 100
            """,
            safeguardMapper
        )
    }
}

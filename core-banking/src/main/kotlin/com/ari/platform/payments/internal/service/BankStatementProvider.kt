package com.ari.platform.payments.internal.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Interface for fetching bank statement data from safeguarding bank accounts.
 *
 * REGULATORY REQUIREMENTS (TCMB & EMD2):
 * - Safeguarding: Customer funds must be held in segregated bank accounts
 * - Reconciliation: Daily comparison of ledger vs bank balances
 * - Reporting: Discrepancies must be investigated within 24 hours
 *
 * INTEGRATION OPTIONS:
 * 1. SWIFT MT940/MT942 statements via SFTP
 * 2. ISO 20022 camt.053 statements
 * 3. Bank-specific API (varies by bank)
 * 4. PSD2 Account Information Service (EU only)
 *
 * This provider supports multiple banks per region:
 * - Turkey: Requires licensed Turkish bank (e.g., Akbank, Garanti)
 * - EU: Lithuania-based EMI partner or bank (e.g., Revolut, Paysera)
 */
interface BankStatementProvider {

    /**
     * Fetch the current balance for a safeguarding account.
     */
    fun getCurrentBalance(bankAccountId: String): BankBalance?

    /**
     * Fetch statement for a date range.
     */
    fun getStatement(bankAccountId: String, fromDate: LocalDate, toDate: LocalDate): BankStatement?

    /**
     * Fetch transactions for a specific date.
     */
    fun getTransactions(bankAccountId: String, date: LocalDate): List<BankTransaction>

    /**
     * Check connection health.
     */
    fun healthCheck(): Boolean
}

data class BankBalance(
    val accountId: String,
    val currency: String,
    val availableBalance: BigDecimal,
    val currentBalance: BigDecimal,
    val pendingCredits: BigDecimal,
    val pendingDebits: BigDecimal,
    val asOfTimestamp: Instant
)

data class BankStatement(
    val accountId: String,
    val currency: String,
    val openingBalance: BigDecimal,
    val closingBalance: BigDecimal,
    val totalCredits: BigDecimal,
    val totalDebits: BigDecimal,
    val transactionCount: Int,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val statementId: String
)

data class BankTransaction(
    val transactionId: String,
    val accountId: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    val valueDate: LocalDate,
    val bookingDate: LocalDate,
    val reference: String?,
    val counterpartyName: String?,
    val counterpartyIban: String?,
    val description: String?,
    val status: TransactionStatus
)

enum class TransactionType {
    CREDIT, DEBIT
}

enum class TransactionStatus {
    BOOKED, PENDING, REVERSED
}

/**
 * Implementation that stores and retrieves bank data from local database.
 * In production, this would be populated by:
 * 1. Scheduled SFTP download of MT940 files
 * 2. Webhook callbacks from banking API
 * 3. PSD2 AISP calls
 */
@Service
class DatabaseBankStatementProvider(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) : BankStatementProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCurrentBalance(bankAccountId: String): BankBalance? {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.bank_account_balances
            WHERE bank_account_id = ?
            ORDER BY as_of_timestamp DESC
            LIMIT 1
            """,
            { rs, _ ->
                BankBalance(
                    accountId = rs.getString("bank_account_id"),
                    currency = rs.getString("currency"),
                    availableBalance = rs.getBigDecimal("available_balance"),
                    currentBalance = rs.getBigDecimal("current_balance"),
                    pendingCredits = rs.getBigDecimal("pending_credits") ?: BigDecimal.ZERO,
                    pendingDebits = rs.getBigDecimal("pending_debits") ?: BigDecimal.ZERO,
                    asOfTimestamp = rs.getTimestamp("as_of_timestamp").toInstant()
                )
            },
            bankAccountId
        ).firstOrNull()
    }

    override fun getStatement(
        bankAccountId: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): BankStatement? {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.bank_statements
            WHERE bank_account_id = ?
              AND from_date >= ?
              AND to_date <= ?
            ORDER BY to_date DESC
            LIMIT 1
            """,
            { rs, _ ->
                BankStatement(
                    accountId = rs.getString("bank_account_id"),
                    currency = rs.getString("currency"),
                    openingBalance = rs.getBigDecimal("opening_balance"),
                    closingBalance = rs.getBigDecimal("closing_balance"),
                    totalCredits = rs.getBigDecimal("total_credits"),
                    totalDebits = rs.getBigDecimal("total_debits"),
                    transactionCount = rs.getInt("transaction_count"),
                    fromDate = rs.getDate("from_date").toLocalDate(),
                    toDate = rs.getDate("to_date").toLocalDate(),
                    statementId = rs.getString("statement_id")
                )
            },
            bankAccountId,
            java.sql.Date.valueOf(fromDate),
            java.sql.Date.valueOf(toDate)
        ).firstOrNull()
    }

    override fun getTransactions(bankAccountId: String, date: LocalDate): List<BankTransaction> {
        return jdbcTemplate.query(
            """
            SELECT * FROM payments.bank_transactions
            WHERE bank_account_id = ?
              AND booking_date = ?
            ORDER BY created_at
            """,
            { rs, _ ->
                BankTransaction(
                    transactionId = rs.getString("transaction_id"),
                    accountId = rs.getString("bank_account_id"),
                    type = TransactionType.valueOf(rs.getString("type")),
                    amount = rs.getBigDecimal("amount"),
                    currency = rs.getString("currency"),
                    valueDate = rs.getDate("value_date").toLocalDate(),
                    bookingDate = rs.getDate("booking_date").toLocalDate(),
                    reference = rs.getString("reference"),
                    counterpartyName = rs.getString("counterparty_name"),
                    counterpartyIban = rs.getString("counterparty_iban"),
                    description = rs.getString("description"),
                    status = TransactionStatus.valueOf(rs.getString("status"))
                )
            },
            bankAccountId,
            java.sql.Date.valueOf(date)
        )
    }

    override fun healthCheck(): Boolean {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT 1 FROM payments.bank_account_balances LIMIT 1",
                Int::class.java
            )
            true
        } catch (e: Exception) {
            log.warn("Bank statement provider health check failed: {}", e.message)
            false
        }
    }

    /**
     * Import an MT940 statement file.
     * Called by scheduled job that downloads from SFTP.
     */
    fun importMt940Statement(bankAccountId: String, mt940Content: String) {
        log.info("Importing MT940 statement for account {}", bankAccountId)

        val parsed = parseMt940(mt940Content)

        // Save statement
        jdbcTemplate.update(
            """
            INSERT INTO payments.bank_statements
                (id, bank_account_id, currency, opening_balance, closing_balance,
                 total_credits, total_debits, transaction_count, from_date, to_date,
                 statement_id, raw_content, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (bank_account_id, statement_id) DO UPDATE SET
                closing_balance = EXCLUDED.closing_balance,
                total_credits = EXCLUDED.total_credits,
                total_debits = EXCLUDED.total_debits,
                transaction_count = EXCLUDED.transaction_count,
                updated_at = NOW()
            """,
            UUID.randomUUID(),
            bankAccountId,
            parsed.currency,
            parsed.openingBalance,
            parsed.closingBalance,
            parsed.totalCredits,
            parsed.totalDebits,
            parsed.transactions.size,
            parsed.fromDate,
            parsed.toDate,
            parsed.statementId,
            mt940Content
        )

        // Save transactions
        for (tx in parsed.transactions) {
            jdbcTemplate.update(
                """
                INSERT INTO payments.bank_transactions
                    (transaction_id, bank_account_id, type, amount, currency,
                     value_date, booking_date, reference, counterparty_name,
                     counterparty_iban, description, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (bank_account_id, transaction_id) DO NOTHING
                """,
                tx.transactionId,
                bankAccountId,
                tx.type.name,
                tx.amount,
                tx.currency,
                tx.valueDate,
                tx.bookingDate,
                tx.reference,
                tx.counterpartyName,
                tx.counterpartyIban,
                tx.description,
                tx.status.name
            )
        }

        // Update balance
        jdbcTemplate.update(
            """
            INSERT INTO payments.bank_account_balances
                (id, bank_account_id, currency, available_balance, current_balance,
                 pending_credits, pending_debits, as_of_timestamp, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            UUID.randomUUID(),
            bankAccountId,
            parsed.currency,
            parsed.closingBalance,
            parsed.closingBalance,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Instant.now()
        )

        log.info(
            "Imported MT940: account={}, transactions={}, closing={}",
            bankAccountId, parsed.transactions.size, parsed.closingBalance
        )
    }

    /**
     * Parse MT940 format (SWIFT bank statement).
     * Simplified parser - production would use a proper MT940 library.
     */
    private fun parseMt940(content: String): ParsedStatement {
        // MT940 field tags:
        // :20: Transaction Reference Number
        // :25: Account Identification
        // :28C: Statement Number/Sequence Number
        // :60F: Opening Balance
        // :61: Statement Line (transaction)
        // :62F: Closing Balance (Booked Funds)

        // This is a simplified placeholder
        // In production, use a library like prowide-iso20022

        return ParsedStatement(
            statementId = "STMT-${System.currentTimeMillis()}",
            currency = "TRY",
            openingBalance = BigDecimal.ZERO,
            closingBalance = BigDecimal.ZERO,
            totalCredits = BigDecimal.ZERO,
            totalDebits = BigDecimal.ZERO,
            fromDate = LocalDate.now().minusDays(1),
            toDate = LocalDate.now().minusDays(1),
            transactions = emptyList()
        )
    }

    private data class ParsedStatement(
        val statementId: String,
        val currency: String,
        val openingBalance: BigDecimal,
        val closingBalance: BigDecimal,
        val totalCredits: BigDecimal,
        val totalDebits: BigDecimal,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val transactions: List<BankTransaction>
    )
}

/**
 * Mock provider for development/testing.
 * Returns ledger balance as bank balance.
 */
@Service
class MockBankStatementProvider : BankStatementProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCurrentBalance(bankAccountId: String): BankBalance? {
        log.debug("Mock: returning null balance for {}", bankAccountId)
        return null
    }

    override fun getStatement(
        bankAccountId: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): BankStatement? {
        log.debug("Mock: returning null statement for {}", bankAccountId)
        return null
    }

    override fun getTransactions(bankAccountId: String, date: LocalDate): List<BankTransaction> {
        log.debug("Mock: returning empty transactions for {}", bankAccountId)
        return emptyList()
    }

    override fun healthCheck(): Boolean {
        return true
    }
}

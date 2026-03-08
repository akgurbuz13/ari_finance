package com.ari.platform.payments.internal.service

import com.ari.platform.ledger.internal.model.AccountType
import com.ari.platform.ledger.internal.service.AccountService
import com.ari.platform.payments.internal.model.PaymentStatus
import com.ari.platform.payments.internal.repository.PaymentOrderRepository
import com.ari.platform.payments.internal.repository.RailReconciliation
import com.ari.platform.payments.internal.repository.RailReferenceRepository
import com.ari.platform.payments.internal.repository.ReconciliationRepository
import com.ari.platform.payments.internal.repository.SafeguardingBalance
import com.ari.platform.shared.config.RegionConfig
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Runs daily reconciliation between:
 * 1. Internal ledger balances and rail settlement totals
 * 2. Safeguarding account ledger balances and bank statement balances
 *
 * Any discrepancies are flagged for manual review via the admin console.
 */
@Service
class ReconciliationService(
    private val reconciliationRepository: ReconciliationRepository,
    private val railReferenceRepository: RailReferenceRepository,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val accountService: AccountService,
    private val regionConfig: RegionConfig,
    private val jdbcTemplate: JdbcTemplate,
    private val bankStatementProvider: DatabaseBankStatementProvider
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Run daily reconciliation at 02:00 AM.
     * Compares yesterday's rail settlements against ledger postings.
     */
    @Scheduled(cron = "0 0 2 * * *")
    fun runDailyReconciliation() {
        val yesterday = LocalDate.now().minusDays(1)
        log.info("Starting daily reconciliation for date={}", yesterday)

        reconcileRailSettlements(yesterday)
        reconcileSafeguardingBalances(yesterday)

        log.info("Daily reconciliation completed for date={}", yesterday)
    }

    /**
     * Reconcile rail settlement totals against ledger for a given date.
     * Groups by rail provider and currency.
     */
    fun reconcileRailSettlements(date: LocalDate) {
        val providers = listOf("fast", "eft", "sepa")
        val currencies = listOf("TRY", "EUR")

        for (provider in providers) {
            for (currency in currencies) {
                try {
                    reconcileProviderCurrency(provider, currency, date)
                } catch (e: Exception) {
                    log.error("Reconciliation failed for provider={}, currency={}, date={}: {}",
                        provider, currency, date, e.message)
                }
            }
        }
    }

    private fun reconcileProviderCurrency(provider: String, currency: String, date: LocalDate) {
        // Get completed payments for this provider/currency/date from our ledger
        val ledgerTotals = jdbcTemplate.queryForMap(
            """
            SELECT
                COALESCE(SUM(po.amount), 0) as total_amount,
                COUNT(*) as total_count
            FROM payments.payment_orders po
            JOIN payments.rail_references rr ON rr.payment_order_id = po.id
            WHERE rr.provider = ?
              AND po.currency = ?
              AND po.status = 'completed'
              AND DATE(po.completed_at) = ?
            """,
            provider, currency, java.sql.Date.valueOf(date)
        )

        val expectedAmount = (ledgerTotals["total_amount"] as? BigDecimal) ?: BigDecimal.ZERO
        val matchedCount = (ledgerTotals["total_count"] as? Long)?.toInt() ?: 0

        // Get confirmed rail references for comparison
        val railTotals = jdbcTemplate.queryForMap(
            """
            SELECT
                COALESCE(SUM(po.amount), 0) as total_amount,
                COUNT(*) as total_count
            FROM payments.rail_references rr
            JOIN payments.payment_orders po ON po.id = rr.payment_order_id
            WHERE rr.provider = ?
              AND po.currency = ?
              AND rr.status = 'confirmed'
              AND DATE(rr.updated_at) = ?
            """,
            provider, currency, java.sql.Date.valueOf(date)
        )

        val actualAmount = (railTotals["total_amount"] as? BigDecimal) ?: BigDecimal.ZERO

        // Count unmatched (submitted but not confirmed)
        val unmatchedCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM payments.rail_references rr
            JOIN payments.payment_orders po ON po.id = rr.payment_order_id
            WHERE rr.provider = ?
              AND po.currency = ?
              AND rr.status = 'submitted'
              AND DATE(rr.created_at) = ?
            """,
            Long::class.java,
            provider, currency, java.sql.Date.valueOf(date)
        ).toInt()

        // Skip if no activity for this provider/currency
        if (expectedAmount == BigDecimal.ZERO && actualAmount == BigDecimal.ZERO && unmatchedCount == 0) {
            return
        }

        val discrepancy = expectedAmount.subtract(actualAmount).abs()
        val status = when {
            discrepancy == BigDecimal.ZERO && unmatchedCount == 0 -> "matched"
            else -> "discrepancy"
        }

        reconciliationRepository.saveReconciliation(
            RailReconciliation(
                railProvider = provider,
                reconciliationDate = date,
                expectedAmount = expectedAmount,
                actualAmount = actualAmount,
                currency = currency,
                discrepancy = discrepancy,
                matchedCount = matchedCount,
                unmatchedCount = unmatchedCount,
                status = status,
                notes = if (unmatchedCount > 0) "$unmatchedCount payments still pending rail confirmation" else null
            )
        )

        if (status == "discrepancy") {
            log.warn(
                "Reconciliation discrepancy: provider={}, currency={}, date={}, expected={}, actual={}, diff={}",
                provider, currency, date, expectedAmount, actualAmount, discrepancy
            )
        } else {
            log.info(
                "Reconciliation matched: provider={}, currency={}, date={}, amount={}, count={}",
                provider, currency, date, expectedAmount, matchedCount
            )
        }
    }

    /**
     * Compare safeguarding account ledger balances against expected bank balances.
     * Fetches bank balances from bank statement provider (MT940/API integration).
     *
     * REGULATORY REQUIREMENTS:
     * - TCMB: Koruma Hesabı reconciliation must be done daily
     * - EMD2: Safeguarding accounts must be segregated and reconciled
     * - Discrepancies > threshold must be reported within 24 hours
     */
    fun reconcileSafeguardingBalances(date: LocalDate) {
        val currencies = listOf("TRY", "EUR")
        val region = regionConfig.region.code

        for (currency in currencies) {
            try {
                reconcileSafeguardingForCurrency(currency, region, date)
            } catch (e: Exception) {
                log.error("Safeguarding reconciliation failed for currency={}: {}", currency, e.message)

                // Record the failure
                reconciliationRepository.saveSafeguardingBalance(
                    SafeguardingBalance(
                        currency = currency,
                        region = region,
                        bankName = getBankName(region),
                        bankAccount = "ERROR",
                        ledgerBalance = BigDecimal.ZERO,
                        bankBalance = BigDecimal.ZERO,
                        discrepancy = BigDecimal.ZERO,
                        asOfDate = date,
                        reconciled = false,
                        notes = "Reconciliation failed: ${e.message}"
                    )
                )
            }
        }
    }

    private fun reconcileSafeguardingForCurrency(currency: String, region: String, date: LocalDate) {
        val safeguardingAccount = try {
            accountService.getOrCreateSystemAccount(currency, AccountType.SAFEGUARDING)
        } catch (e: Exception) {
            log.debug("No safeguarding account for currency={}", currency)
            return
        }

        val ledgerBalance = accountService.getBalance(safeguardingAccount.id)
        val bankAccountId = safeguardingAccount.iban ?: safeguardingAccount.id.toString()

        // Fetch bank balance from statement provider
        val bankBalanceData = bankStatementProvider.getCurrentBalance(bankAccountId)
        val bankBalance = bankBalanceData?.currentBalance

        val (finalBankBalance, reconciliationStatus, notes) = when {
            bankBalance != null -> {
                // We have actual bank data
                val discrepancy = ledgerBalance.subtract(bankBalance).abs()
                val toleranceAmount = BigDecimal("0.01") // 1 cent tolerance for rounding

                val status = when {
                    discrepancy <= toleranceAmount -> "MATCHED"
                    discrepancy <= BigDecimal("100") -> "MINOR_DISCREPANCY"
                    else -> "MAJOR_DISCREPANCY"
                }

                Triple(bankBalance, status, buildReconciliationNotes(discrepancy, status, bankBalanceData))
            }
            else -> {
                // No bank data available - use ledger as estimate with warning
                log.warn("No bank balance available for safeguarding account {}, using ledger balance", bankAccountId)
                Triple(ledgerBalance, "PENDING_BANK_DATA", "Bank statement not yet received")
            }
        }

        val discrepancy = ledgerBalance.subtract(finalBankBalance).abs()
        val reconciled = discrepancy == BigDecimal.ZERO ||
            (discrepancy <= BigDecimal("0.01") && reconciliationStatus == "MATCHED")

        reconciliationRepository.saveSafeguardingBalance(
            SafeguardingBalance(
                currency = currency,
                region = region,
                bankName = getBankName(region),
                bankAccount = bankAccountId,
                ledgerBalance = ledgerBalance,
                bankBalance = finalBankBalance,
                discrepancy = discrepancy,
                asOfDate = date,
                reconciled = reconciled,
                notes = notes
            )
        )

        // Alert on major discrepancies
        if (reconciliationStatus == "MAJOR_DISCREPANCY") {
            alertOnMajorDiscrepancy(currency, region, ledgerBalance, finalBankBalance, discrepancy, date)
        }

        log.info(
            "Safeguarding reconciliation: currency={}, region={}, ledger={}, bank={}, status={}",
            currency, region, ledgerBalance, finalBankBalance, reconciliationStatus
        )
    }

    private fun buildReconciliationNotes(
        discrepancy: BigDecimal,
        status: String,
        bankData: BankBalance?
    ): String? {
        if (discrepancy == BigDecimal.ZERO) return null

        val notes = StringBuilder()
        notes.append("Status: $status. ")
        notes.append("Discrepancy: $discrepancy. ")

        if (bankData != null) {
            if (bankData.pendingCredits > BigDecimal.ZERO) {
                notes.append("Pending credits: ${bankData.pendingCredits}. ")
            }
            if (bankData.pendingDebits > BigDecimal.ZERO) {
                notes.append("Pending debits: ${bankData.pendingDebits}. ")
            }
            notes.append("Bank timestamp: ${bankData.asOfTimestamp}")
        }

        return notes.toString()
    }

    private fun alertOnMajorDiscrepancy(
        currency: String,
        region: String,
        ledgerBalance: BigDecimal,
        bankBalance: BigDecimal,
        discrepancy: BigDecimal,
        date: LocalDate
    ) {
        log.error(
            "MAJOR SAFEGUARDING DISCREPANCY: currency={}, region={}, date={}, " +
            "ledger={}, bank={}, discrepancy={}",
            currency, region, date, ledgerBalance, bankBalance, discrepancy
        )

        // Create compliance alert
        jdbcTemplate.update(
            """
            INSERT INTO shared.threshold_alerts
                (alert_type, user_id, amount, currency, description, status)
            VALUES ('SAFEGUARDING_DISCREPANCY', ?, ?, ?, ?, 'PENDING')
            """,
            UUID.fromString("00000000-0000-0000-0000-000000000000"), // System user
            discrepancy,
            currency,
            "Major safeguarding discrepancy for $region on $date: ledger=$ledgerBalance, bank=$bankBalance"
        )
    }

    /**
     * Perform detailed transaction-level reconciliation.
     * Matches individual ledger entries against bank transactions.
     */
    fun reconcileTransactions(bankAccountId: String, date: LocalDate): TransactionReconciliationResult {
        log.info("Starting transaction-level reconciliation for {} on {}", bankAccountId, date)

        val bankTransactions = bankStatementProvider.getTransactions(bankAccountId, date)
        if (bankTransactions.isEmpty()) {
            log.info("No bank transactions for {} on {}", bankAccountId, date)
            return TransactionReconciliationResult(
                date = date,
                bankAccountId = bankAccountId,
                matchedCount = 0,
                unmatchedBankCount = 0,
                unmatchedLedgerCount = 0,
                totalBankAmount = BigDecimal.ZERO,
                totalLedgerAmount = BigDecimal.ZERO
            )
        }

        // Get ledger entries for the same period
        val ledgerEntries = jdbcTemplate.queryForList(
            """
            SELECT e.id, e.amount, e.direction, t.reference_id, e.created_at
            FROM ledger.entries e
            JOIN ledger.accounts a ON a.id = e.account_id
            JOIN ledger.transactions t ON t.id = e.transaction_id
            WHERE a.iban = ? AND DATE(e.created_at) = ?
            """,
            bankAccountId, java.sql.Date.valueOf(date)
        )

        // Match transactions
        var matchedCount = 0
        val unmatchedBank = mutableListOf<BankTransaction>()
        val unmatchedLedger = ledgerEntries.toMutableList()

        for (bankTx in bankTransactions) {
            val matchingLedger = unmatchedLedger.find { ledger ->
                val ledgerAmount = ledger["amount"] as BigDecimal
                val referenceId = ledger["reference_id"] as? String
                ledgerAmount == bankTx.amount &&
                    (referenceId == bankTx.reference || referenceId == bankTx.transactionId)
            }

            if (matchingLedger != null) {
                matchedCount++
                unmatchedLedger.remove(matchingLedger)
            } else {
                unmatchedBank.add(bankTx)
            }
        }

        val result = TransactionReconciliationResult(
            date = date,
            bankAccountId = bankAccountId,
            matchedCount = matchedCount,
            unmatchedBankCount = unmatchedBank.size,
            unmatchedLedgerCount = unmatchedLedger.size,
            totalBankAmount = bankTransactions.sumOf { it.amount },
            totalLedgerAmount = ledgerEntries.sumOf { it["amount"] as BigDecimal }
        )

        log.info(
            "Transaction reconciliation: {} matched, {} unmatched bank, {} unmatched ledger",
            matchedCount, unmatchedBank.size, unmatchedLedger.size
        )

        return result
    }

    data class TransactionReconciliationResult(
        val date: LocalDate,
        val bankAccountId: String,
        val matchedCount: Int,
        val unmatchedBankCount: Int,
        val unmatchedLedgerCount: Int,
        val totalBankAmount: BigDecimal,
        val totalLedgerAmount: BigDecimal
    ) {
        val fullyReconciled: Boolean
            get() = unmatchedBankCount == 0 && unmatchedLedgerCount == 0
    }

    /**
     * Get safeguarding summary report for regulatory reporting.
     */
    fun getSafeguardingSummary(asOfDate: LocalDate): SafeguardingSummary {
        val balances = jdbcTemplate.query(
            """
            SELECT currency, region, SUM(ledger_balance) as total_ledger,
                   SUM(bank_balance) as total_bank, SUM(discrepancy) as total_discrepancy,
                   BOOL_AND(reconciled) as all_reconciled
            FROM payments.safeguarding_balances
            WHERE as_of_date = ?
            GROUP BY currency, region
            """,
            { rs, _ ->
                CurrencyBalance(
                    currency = rs.getString("currency"),
                    region = rs.getString("region"),
                    ledgerBalance = rs.getBigDecimal("total_ledger"),
                    bankBalance = rs.getBigDecimal("total_bank"),
                    discrepancy = rs.getBigDecimal("total_discrepancy"),
                    reconciled = rs.getBoolean("all_reconciled")
                )
            },
            java.sql.Date.valueOf(asOfDate)
        )

        return SafeguardingSummary(
            asOfDate = asOfDate,
            balances = balances,
            totalCustomerFunds = balances.sumOf { it.ledgerBalance },
            totalBankFunds = balances.sumOf { it.bankBalance },
            allReconciled = balances.all { it.reconciled }
        )
    }

    data class SafeguardingSummary(
        val asOfDate: LocalDate,
        val balances: List<CurrencyBalance>,
        val totalCustomerFunds: BigDecimal,
        val totalBankFunds: BigDecimal,
        val allReconciled: Boolean
    )

    data class CurrencyBalance(
        val currency: String,
        val region: String,
        val ledgerBalance: BigDecimal,
        val bankBalance: BigDecimal,
        val discrepancy: BigDecimal,
        val reconciled: Boolean
    )

    private fun getBankName(region: String): String {
        return when (region) {
            "TR" -> "ARI Safeguarding - Turkiye"
            "EU" -> "ARI Safeguarding - Lithuania"
            else -> "ARI Safeguarding"
        }
    }
}

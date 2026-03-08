package com.ari.platform.compliance.internal.service

import com.ari.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class TransactionMonitoringService(
    private val jdbcTemplate: JdbcTemplate,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val DAILY_LIMIT_TRY = BigDecimal("500000")
        val DAILY_LIMIT_EUR = BigDecimal("15000")
        val SINGLE_TX_ALERT_TRY = BigDecimal("100000")
        val SINGLE_TX_ALERT_EUR = BigDecimal("3000")
    }

    data class MonitoringResult(
        val allowed: Boolean,
        val alerts: List<String> = emptyList(),
        val requiresReview: Boolean = false
    )

    fun checkTransaction(
        userId: UUID,
        amount: BigDecimal,
        currency: String,
        transactionType: String
    ): MonitoringResult {
        val alerts = mutableListOf<String>()
        var requiresReview = false

        // Rule 1: Single transaction amount threshold
        val singleTxLimit = if (currency == "TRY") SINGLE_TX_ALERT_TRY else SINGLE_TX_ALERT_EUR
        if (amount >= singleTxLimit) {
            alerts.add("Large transaction: $amount $currency exceeds alert threshold")
            requiresReview = true
        }

        // Rule 2: Daily limit check using actual daily total from ledger
        val dailyLimit = if (currency == "TRY") DAILY_LIMIT_TRY else DAILY_LIMIT_EUR
        val existingDailyTotal = getDailyDebitTotal(userId, currency)
        val projectedDailyTotal = existingDailyTotal + amount

        if (projectedDailyTotal > dailyLimit) {
            log.warn(
                "User {} exceeded daily limit: existing={} + current={} = {} {} (limit={})",
                userId, existingDailyTotal, amount, projectedDailyTotal, currency, dailyLimit
            )
            return MonitoringResult(
                allowed = false,
                alerts = listOf("Daily limit exceeded: projected total $projectedDailyTotal $currency exceeds limit of $dailyLimit $currency"),
                requiresReview = false
            )
        }

        if (alerts.isNotEmpty()) {
            auditService.log(
                actorId = null,
                actorType = "system",
                action = "transaction_monitoring_alert",
                resourceType = "user",
                resourceId = userId.toString(),
                details = mapOf("alerts" to alerts, "amount" to amount.toPlainString(), "currency" to currency)
            )
        }

        return MonitoringResult(
            allowed = true,
            alerts = alerts,
            requiresReview = requiresReview
        )
    }

    /**
     * Queries the sum of all debit entries from the user's accounts in the last 24 hours
     * for the given currency. This joins ledger.entries with ledger.accounts to find
     * all accounts belonging to the user, then sums debit amounts.
     */
    private fun getDailyDebitTotal(userId: UUID, currency: String): BigDecimal {
        val sql = """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entries e
            JOIN ledger.accounts a ON e.account_id = a.id
            WHERE a.user_id = ?
              AND e.currency = ?
              AND e.direction = 'debit'
              AND e.created_at >= now() - INTERVAL '24 hours'
              AND a.account_type = 'user_wallet'
        """
        return jdbcTemplate.queryForObject(sql, BigDecimal::class.java, userId, currency)
            ?: BigDecimal.ZERO
    }
}

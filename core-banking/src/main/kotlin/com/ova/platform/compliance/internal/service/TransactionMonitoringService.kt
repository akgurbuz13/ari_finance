package com.ova.platform.compliance.internal.service

import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class TransactionMonitoringService(
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

        // Rule 2: Daily limit check
        // TODO: Query actual daily total from ledger
        val dailyLimit = if (currency == "TRY") DAILY_LIMIT_TRY else DAILY_LIMIT_EUR
        if (amount > dailyLimit) {
            log.warn("User {} exceeded daily limit: {} {}", userId, amount, currency)
            return MonitoringResult(
                allowed = false,
                alerts = listOf("Daily limit exceeded"),
                requiresReview = false
            )
        }

        // Rule 3: Velocity check
        // TODO: Check transaction frequency from Redis/DB

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
}

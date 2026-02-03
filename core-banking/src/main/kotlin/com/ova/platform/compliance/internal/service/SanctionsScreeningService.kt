package com.ova.platform.compliance.internal.service

import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SanctionsScreeningService(
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ScreeningResult(
        val passed: Boolean,
        val matchType: String? = null,
        val matchDetails: String? = null
    )

    fun screenUser(userId: UUID, firstName: String?, lastName: String?): ScreeningResult {
        log.info("Screening user {} against sanctions lists", userId)

        // TODO: Integrate with ComplyAdvantage/Refinitiv API
        // Stub: all users pass screening
        val result = ScreeningResult(passed = true)

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "sanctions_screening",
            resourceType = "user",
            resourceId = userId.toString(),
            details = mapOf("passed" to result.passed)
        )

        return result
    }

    fun screenTransaction(
        senderId: UUID,
        receiverId: UUID,
        amount: java.math.BigDecimal,
        currency: String
    ): ScreeningResult {
        log.info("Screening transaction sender={} receiver={} amount={} {}",
            senderId, receiverId, amount, currency)

        // TODO: Integrate with screening provider
        val result = ScreeningResult(passed = true)

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "transaction_screening",
            resourceType = "payment",
            resourceId = "$senderId->$receiverId",
            details = mapOf(
                "passed" to result.passed,
                "amount" to amount.toPlainString(),
                "currency" to currency
            )
        )

        return result
    }
}

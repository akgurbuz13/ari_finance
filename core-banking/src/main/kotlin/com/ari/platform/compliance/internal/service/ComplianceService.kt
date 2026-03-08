package com.ari.platform.compliance.internal.service

import com.ari.platform.shared.exception.ComplianceRejectedException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class ComplianceService(
    private val sanctionsScreeningService: SanctionsScreeningService,
    private val transactionMonitoringService: TransactionMonitoringService,
    private val caseManagementService: CaseManagementService
) {

    fun checkPayment(
        senderId: UUID,
        receiverId: UUID,
        amount: BigDecimal,
        currency: String,
        transactionType: String
    ) {
        // 1. Sanctions screening
        val screeningResult = sanctionsScreeningService.screenTransaction(
            senderId, receiverId, amount, currency
        )
        if (!screeningResult.passed) {
            caseManagementService.createCase(
                userId = senderId,
                type = "sanctions_match",
                description = "Sanctions match: ${screeningResult.matchDetails}"
            )
            throw ComplianceRejectedException("Sanctions screening failed")
        }

        // 2. Transaction monitoring
        val monitoringResult = transactionMonitoringService.checkTransaction(
            senderId, amount, currency, transactionType
        )
        if (!monitoringResult.allowed) {
            throw ComplianceRejectedException(monitoringResult.alerts.joinToString("; "))
        }

        if (monitoringResult.requiresReview) {
            caseManagementService.createCase(
                userId = senderId,
                type = "transaction_review",
                description = "Transaction requires review: ${monitoringResult.alerts.joinToString("; ")}"
            )
        }
    }
}

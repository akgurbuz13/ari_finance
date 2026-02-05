package com.ova.platform.shared.api

import com.ova.platform.payments.internal.model.PaymentStatus
import com.ova.platform.payments.internal.model.PaymentType
import com.ova.platform.payments.internal.repository.PaymentOrderRepository
import com.ova.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ova.platform.payments.internal.model.PaymentStatusHistory
import com.ova.platform.payments.internal.service.DepositService
import com.ova.platform.payments.internal.service.WithdrawalService
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Internal API for blockchain service callbacks.
 * Called when on-chain settlement (mint/burn) is confirmed or fails.
 * Protected by SYSTEM role (service-to-service auth).
 */
@RestController
@RequestMapping("/api/internal")
class InternalSettlementController(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusHistoryRepository: PaymentStatusHistoryRepository,
    private val depositService: DepositService,
    private val withdrawalService: WithdrawalService,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/settlement-confirmed")
    fun onSettlementConfirmed(@RequestBody request: SettlementConfirmationRequest): ResponseEntity<Map<String, String>> {
        log.info("Settlement confirmation received: paymentOrderId={}, operation={}, success={}",
            request.paymentOrderId, request.operation, request.success)

        val paymentOrderId = UUID.fromString(request.paymentOrderId)
        val order = paymentOrderRepository.findById(paymentOrderId)

        if (order == null) {
            log.warn("Payment order not found: {}", request.paymentOrderId)
            return ResponseEntity.ok(mapOf("status" to "not_found"))
        }

        if (request.success) {
            when (order.type) {
                PaymentType.DEPOSIT -> {
                    if (order.status == PaymentStatus.SETTLING) {
                        depositService.completeDeposit(paymentOrderId)
                    }
                }
                PaymentType.WITHDRAWAL -> {
                    if (order.status == PaymentStatus.SETTLING) {
                        withdrawalService.completeWithdrawal(paymentOrderId)
                    }
                }
                PaymentType.CROSS_BORDER -> {
                    // Cross-border has multiple settlement steps (burn + mint)
                    // Track completion per operation
                    handleCrossBorderSettlement(paymentOrderId, request.operation, request.txHash)
                }
                else -> {
                    log.debug("No settlement action for payment type={}", order.type)
                }
            }
        } else {
            // Settlement failed on-chain
            log.error("Settlement FAILED: paymentOrderId={}, operation={}", request.paymentOrderId, request.operation)

            when (order.type) {
                PaymentType.WITHDRAWAL -> {
                    if (order.status == PaymentStatus.SETTLING) {
                        withdrawalService.reverseWithdrawal(paymentOrderId, "On-chain settlement failed")
                    }
                }
                else -> {
                    // Mark as failed
                    paymentOrderRepository.updateStatus(paymentOrderId, PaymentStatus.FAILED)
                    statusHistoryRepository.save(
                        PaymentStatusHistory(
                            paymentOrderId = paymentOrderId,
                            fromStatus = order.status,
                            toStatus = PaymentStatus.FAILED,
                            reason = "On-chain ${request.operation} failed"
                        )
                    )
                }
            }
        }

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "settlement_${if (request.success) "confirmed" else "failed"}",
            resourceType = "payment",
            resourceId = request.paymentOrderId,
            details = mapOf(
                "operation" to request.operation,
                "txHash" to (request.txHash ?: ""),
                "success" to request.success
            )
        )

        return ResponseEntity.ok(mapOf("status" to "processed"))
    }

    private fun handleCrossBorderSettlement(paymentOrderId: UUID, operation: String, txHash: String?) {
        // Cross-border transfers require both a burn (source chain) and mint (destination chain).
        // We update metadata to track which leg has been confirmed.
        val order = paymentOrderRepository.findById(paymentOrderId) ?: return

        val metadata = order.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["${operation}_tx_hash"] = txHash ?: ""
        metadata["${operation}_confirmed"] = "true"

        val burnConfirmed = metadata["burn_confirmed"] == "true"
        val mintConfirmed = metadata["mint_confirmed"] == "true"

        if (burnConfirmed && mintConfirmed) {
            // Both legs confirmed — mark cross-border transfer as completed
            paymentOrderRepository.updateStatus(paymentOrderId, PaymentStatus.COMPLETED)
            statusHistoryRepository.save(
                PaymentStatusHistory(
                    paymentOrderId = paymentOrderId,
                    fromStatus = PaymentStatus.SETTLING,
                    toStatus = PaymentStatus.COMPLETED,
                    reason = "Both on-chain legs confirmed (burn + mint)"
                )
            )
            log.info("Cross-border payment {} fully settled on-chain", paymentOrderId)
        } else {
            log.info("Cross-border payment {} partially settled: burn={}, mint={}",
                paymentOrderId, burnConfirmed, mintConfirmed)
        }
    }
}

data class SettlementConfirmationRequest(
    val paymentOrderId: String,
    val operation: String,
    val txHash: String?,
    val success: Boolean
)

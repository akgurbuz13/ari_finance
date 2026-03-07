package com.ova.platform.shared.api

import com.ova.platform.ledger.internal.model.AccountType
import com.ova.platform.ledger.internal.model.EntryDirection
import com.ova.platform.ledger.internal.model.PostingInstruction
import com.ova.platform.ledger.internal.model.TransactionType
import com.ova.platform.ledger.internal.service.AccountService
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.payments.event.PaymentCompleted
import com.ova.platform.payments.internal.model.PaymentOrder
import com.ova.platform.payments.internal.model.PaymentStatus
import com.ova.platform.payments.internal.model.PaymentType
import com.ova.platform.payments.internal.repository.PaymentOrderRepository
import com.ova.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ova.platform.payments.internal.model.PaymentStatusHistory
import com.ova.platform.payments.internal.service.DepositService
import com.ova.platform.payments.internal.service.VehicleEscrowService
import com.ova.platform.payments.internal.service.WithdrawalService
import com.ova.platform.shared.event.OutboxPublisher
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
    private val ledgerService: LedgerService,
    private val accountService: AccountService,
    private val outboxPublisher: OutboxPublisher,
    private val vehicleEscrowService: VehicleEscrowService,
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
                    handleCrossBorderSettlement(order, request.operation, request.txHash)
                }
                PaymentType.CROSS_BORDER_SAME_CCY -> {
                    // Same-currency cross-border: single bridge_transfer operation
                    if (order.status == PaymentStatus.SETTLING) {
                        handleSameCcyCrossBorderSettlement(order, request.txHash)
                    }
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

    @PostMapping("/vehicle-settlement")
    fun onVehicleSettlement(@RequestBody request: VehicleSettlementRequest): ResponseEntity<Map<String, String>> {
        log.info("Vehicle settlement: type={}, escrowId={}", request.type, request.escrowId)

        try {
            when (request.type) {
                "VEHICLE_MINTED" -> vehicleEscrowService.onVehicleMinted(
                    UUID.fromString(request.vehicleRegistrationId!!),
                    request.tokenId!!,
                    request.txHash ?: ""
                )
                "ESCROW_SETUP_CONFIRMED" -> vehicleEscrowService.onEscrowSetupConfirmed(
                    UUID.fromString(request.escrowId!!),
                    request.onChainEscrowId!!,
                    request.txHash ?: ""
                )
                "ESCROW_FUNDED" -> vehicleEscrowService.onEscrowFunded(
                    UUID.fromString(request.escrowId!!),
                    request.txHash ?: ""
                )
                "ESCROW_CONFIRMED" -> vehicleEscrowService.onEscrowConfirmed(
                    UUID.fromString(request.escrowId!!),
                    request.role ?: "SELLER",
                    request.completed ?: false,
                    request.txHash ?: ""
                )
                "ESCROW_CANCELLED" -> vehicleEscrowService.onEscrowCancelled(
                    UUID.fromString(request.escrowId!!),
                    request.txHash ?: ""
                )
                else -> log.warn("Unknown vehicle settlement type: {}", request.type)
            }
        } catch (e: Exception) {
            log.error("Vehicle settlement failed: type={}, error={}", request.type, e.message, e)
            return ResponseEntity.ok(mapOf("status" to "error", "message" to (e.message ?: "unknown")))
        }

        return ResponseEntity.ok(mapOf("status" to "processed"))
    }

    private fun handleCrossBorderSettlement(order: PaymentOrder, operation: String, txHash: String?) {
        // Cross-border transfers require both a burn (source chain) and mint (destination chain).
        // We update metadata to track which leg has been confirmed.
        val paymentOrderId = order.id
        val metadata = order.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["${operation}_tx_hash"] = txHash ?: ""
        metadata["${operation}_confirmed"] = "true"
        paymentOrderRepository.updateMetadata(paymentOrderId, metadata)

        val burnConfirmed = metadata["burn_confirmed"]?.toString() == "true"
        val mintConfirmed = metadata["mint_confirmed"]?.toString() == "true"

        if (burnConfirmed && mintConfirmed && order.status != PaymentStatus.COMPLETED) {
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

            val ledgerTransactionId = order.ledgerTransactionId
            if (ledgerTransactionId != null) {
                outboxPublisher.publish(
                    PaymentCompleted(
                        paymentOrderId = paymentOrderId,
                        paymentType = PaymentType.CROSS_BORDER.value,
                        ledgerTransactionId = ledgerTransactionId,
                        amount = order.amount,
                        currency = order.currency
                    )
                )
            } else {
                log.warn("Cross-border payment {} has no ledger transaction id at completion", paymentOrderId)
            }

            log.info("Cross-border payment {} fully settled on-chain", paymentOrderId)
        } else {
            log.info("Cross-border payment {} partially settled: burn={}, mint={}",
                paymentOrderId, burnConfirmed, mintConfirmed)
        }
    }
    /**
     * Handle settlement confirmation for same-currency cross-border transfers.
     * This is the critical step: blockchain IS the settlement rail.
     * Only now do we credit the receiver's account.
     */
    private fun handleSameCcyCrossBorderSettlement(order: PaymentOrder, txHash: String?) {
        val paymentOrderId = order.id

        // Update metadata with bridge tx hash
        val metadata = order.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["bridge_tx_hash"] = txHash ?: ""
        metadata["bridge_confirmed"] = "true"
        paymentOrderRepository.updateMetadata(paymentOrderId, metadata)

        // Credit receiver: debit transit -> credit receiver
        val senderAccount = accountService.getAccountById(order.senderAccountId)
        val transitAccount = accountService.getOrCreateSystemAccount(
            order.currency, AccountType.CROSS_BORDER_TRANSIT, senderAccount.region
        )

        val creditPostings = listOf(
            PostingInstruction(
                accountId = transitAccount.id,
                direction = EntryDirection.DEBIT,
                amount = order.amount,
                currency = order.currency
            ),
            PostingInstruction(
                accountId = order.receiverAccountId,
                direction = EntryDirection.CREDIT,
                amount = order.amount,
                currency = order.currency
            )
        )

        val creditTx = ledgerService.postEntries(
            idempotencyKey = "payment:${paymentOrderId}:receiver_credit",
            type = TransactionType.CROSS_BORDER,
            postings = creditPostings,
            referenceId = paymentOrderId.toString(),
            metadata = mapOf(
                "payment_type" to PaymentType.CROSS_BORDER_SAME_CCY.value,
                "leg" to "transit_to_receiver",
                "bridge_tx_hash" to (txHash ?: "")
            )
        )

        // Mark payment completed
        paymentOrderRepository.updateStatus(paymentOrderId, PaymentStatus.COMPLETED)
        statusHistoryRepository.save(
            PaymentStatusHistory(
                paymentOrderId = paymentOrderId,
                fromStatus = PaymentStatus.SETTLING,
                toStatus = PaymentStatus.COMPLETED,
                reason = "On-chain bridge transfer confirmed"
            )
        )

        outboxPublisher.publish(
            PaymentCompleted(
                paymentOrderId = paymentOrderId,
                paymentType = PaymentType.CROSS_BORDER_SAME_CCY.value,
                ledgerTransactionId = creditTx.id,
                amount = order.amount,
                currency = order.currency
            )
        )

        log.info("Same-ccy cross-border payment {} settled: transit -> receiver credited", paymentOrderId)
    }
}

data class SettlementConfirmationRequest(
    val paymentOrderId: String,
    val operation: String,
    val txHash: String?,
    val success: Boolean
)

data class VehicleSettlementRequest(
    val type: String,
    val vehicleRegistrationId: String? = null,
    val escrowId: String? = null,
    val tokenId: Long? = null,
    val onChainEscrowId: Long? = null,
    val role: String? = null,
    val completed: Boolean? = null,
    val txHash: String? = null
)

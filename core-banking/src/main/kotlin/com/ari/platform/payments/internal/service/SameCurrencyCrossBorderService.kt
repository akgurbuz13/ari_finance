package com.ari.platform.payments.internal.service

import com.ari.platform.ledger.internal.model.AccountStatus
import com.ari.platform.ledger.internal.model.AccountType
import com.ari.platform.ledger.internal.model.EntryDirection
import com.ari.platform.ledger.internal.model.PostingInstruction
import com.ari.platform.ledger.internal.model.TransactionType
import com.ari.platform.ledger.internal.service.AccountService
import com.ari.platform.ledger.internal.service.LedgerService
import com.ari.platform.payments.event.CrossBorderBurnMintRequested
import com.ari.platform.payments.event.PaymentInitiated
import com.ari.platform.payments.internal.model.PaymentOrder
import com.ari.platform.payments.internal.model.PaymentStatus
import com.ari.platform.payments.internal.model.PaymentStatusHistory
import com.ari.platform.payments.internal.model.PaymentType
import com.ari.platform.payments.internal.repository.PaymentOrderRepository
import com.ari.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ari.platform.shared.event.OutboxPublisher
import com.ari.platform.shared.exception.BadRequestException
import com.ari.platform.shared.exception.ComplianceRejectedException
import com.ari.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Saga orchestrator for same-currency cross-border transfers (e.g. TRY/TR -> TRY/EU).
 *
 * Key difference from CrossBorderTransferService: no FX conversion needed.
 * Blockchain IS the settlement rail — receiver credited only after on-chain confirmation.
 *
 * Flow:
 * 1. Validate: same currency, both accounts active, different regions
 * 2. Create payment order (type = CROSS_BORDER_SAME_CCY)
 * 3. Compliance check
 * 4. Ledger: DEBIT sender -> CREDIT transit account (receiver NOT credited yet)
 * 5. Publish CrossBorderBurnMintRequested to outbox
 * 6. Status -> SETTLING (blockchain takes over)
 * 7. On settlement callback: DEBIT transit -> CREDIT receiver
 */
@Service
class SameCurrencyCrossBorderService(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusHistoryRepository: PaymentStatusHistoryRepository,
    private val ledgerService: LedgerService,
    private val accountService: AccountService,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService,
    @Value("\${ari.blockchain.tr-l1.chain-id:99999}") private val trChainId: Long,
    @Value("\${ari.blockchain.eu-l1.chain-id:99998}") private val euChainId: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(
        idempotencyKey: String,
        senderAccountId: UUID,
        receiverAccountId: UUID,
        amount: BigDecimal,
        description: String? = null,
        initiatorId: UUID
    ): PaymentOrder {
        // Step 1: Idempotency check
        val existing = paymentOrderRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            log.info("Idempotent replay for same-ccy cross-border key={}, orderId={}", idempotencyKey, existing.id)
            return existing
        }

        // Step 2: Validate accounts
        val senderAccount = accountService.getAccountById(senderAccountId)
        val receiverAccount = accountService.getAccountById(receiverAccountId)

        if (senderAccount.status != AccountStatus.ACTIVE) {
            throw BadRequestException("Sender account $senderAccountId is ${senderAccount.status.value}")
        }
        if (receiverAccount.status != AccountStatus.ACTIVE) {
            throw BadRequestException("Receiver account $receiverAccountId is ${receiverAccount.status.value}")
        }
        if (senderAccount.currency != receiverAccount.currency) {
            throw BadRequestException("Same-currency cross-border requires identical currencies. Use FX cross-border instead.")
        }
        if (senderAccount.region == receiverAccount.region) {
            throw BadRequestException("Same-currency cross-border requires different regions. Use domestic transfer instead.")
        }

        val currency = senderAccount.currency
        val sourceChainId = chainIdForRegion(senderAccount.region)
        val targetChainId = chainIdForRegion(receiverAccount.region)

        // Step 3: Create payment order
        val paymentOrder = paymentOrderRepository.save(
            PaymentOrder(
                idempotencyKey = idempotencyKey,
                type = PaymentType.CROSS_BORDER_SAME_CCY,
                status = PaymentStatus.INITIATED,
                senderAccountId = senderAccountId,
                receiverAccountId = receiverAccountId,
                amount = amount,
                currency = currency,
                description = description,
                metadata = mapOf(
                    "source_region" to senderAccount.region,
                    "target_region" to receiverAccount.region,
                    "source_chain_id" to sourceChainId.toString(),
                    "target_chain_id" to targetChainId.toString()
                )
            )
        )

        recordStatusTransition(paymentOrder.id, null, PaymentStatus.INITIATED, null)

        outboxPublisher.publish(
            PaymentInitiated(
                paymentOrderId = paymentOrder.id,
                paymentType = PaymentType.CROSS_BORDER_SAME_CCY.value,
                senderAccountId = senderAccountId,
                receiverAccountId = receiverAccountId,
                amount = amount,
                currency = currency
            )
        )

        // Step 4: Compliance check
        transitionStatus(paymentOrder.id, PaymentStatus.INITIATED, PaymentStatus.COMPLIANCE_CHECK, null)
        performComplianceCheck(paymentOrder)
        transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.PROCESSING, "Compliance check passed")

        // Step 5: Ledger — debit sender, credit transit (receiver NOT credited)
        transitionStatus(paymentOrder.id, PaymentStatus.PROCESSING, PaymentStatus.SETTLING, null)

        try {
            val transitAccount = accountService.getOrCreateSystemAccount(
                currency, AccountType.CROSS_BORDER_TRANSIT, senderAccount.region
            )

            val postings = listOf(
                PostingInstruction(
                    accountId = senderAccountId,
                    direction = EntryDirection.DEBIT,
                    amount = amount,
                    currency = currency
                ),
                PostingInstruction(
                    accountId = transitAccount.id,
                    direction = EntryDirection.CREDIT,
                    amount = amount,
                    currency = currency
                )
            )

            val ledgerTx = ledgerService.postEntries(
                idempotencyKey = "payment:${paymentOrder.id}:sender_debit",
                type = TransactionType.CROSS_BORDER,
                postings = postings,
                referenceId = paymentOrder.id.toString(),
                metadata = mapOf(
                    "payment_type" to PaymentType.CROSS_BORDER_SAME_CCY.value,
                    "leg" to "sender_debit_to_transit"
                )
            )

            paymentOrderRepository.updateLedgerTransactionId(paymentOrder.id, ledgerTx.id)

            // Step 6: Publish bridge event — blockchain takes over
            outboxPublisher.publish(
                CrossBorderBurnMintRequested(
                    paymentOrderId = paymentOrder.id,
                    sourceAccountId = senderAccountId,
                    targetAccountId = receiverAccountId,
                    amount = amount,
                    currency = currency,
                    sourceChainId = sourceChainId,
                    targetChainId = targetChainId
                )
            )

            auditService.log(
                actorId = initiatorId,
                actorType = "user",
                action = "cross_border_same_ccy_transfer",
                resourceType = "payment_order",
                resourceId = paymentOrder.id.toString(),
                details = mapOf(
                    "sender_account_id" to senderAccountId.toString(),
                    "receiver_account_id" to receiverAccountId.toString(),
                    "amount" to amount.toPlainString(),
                    "currency" to currency,
                    "source_region" to senderAccount.region,
                    "target_region" to receiverAccount.region
                )
            )

            log.info(
                "Same-ccy cross-border initiated: orderId={}, {} {}, {} -> {}",
                paymentOrder.id, amount, currency, senderAccount.region, receiverAccount.region
            )

            return paymentOrder

        } catch (e: Exception) {
            transitionStatus(paymentOrder.id, PaymentStatus.SETTLING, PaymentStatus.FAILED, e.message)
            log.error("Same-ccy cross-border failed for orderId={}: {}", paymentOrder.id, e.message)
            throw e
        }
    }

    private fun chainIdForRegion(region: String): Long = when (region) {
        "TR" -> trChainId
        "EU" -> euChainId
        else -> throw BadRequestException("Unknown region: $region")
    }

    private fun performComplianceCheck(order: PaymentOrder) {
        val crossBorderLimit = BigDecimal("500000.00")
        if (order.amount > crossBorderLimit) {
            transitionStatus(
                order.id,
                PaymentStatus.COMPLIANCE_CHECK,
                PaymentStatus.FAILED,
                "Amount exceeds cross-border transfer limit"
            )
            throw ComplianceRejectedException(
                "Amount exceeds cross-border transfer limit of $crossBorderLimit ${order.currency}"
            )
        }
        log.debug("Same-ccy cross-border compliance check passed for orderId={}", order.id)
    }

    private fun transitionStatus(orderId: UUID, from: PaymentStatus, to: PaymentStatus, reason: String?) {
        paymentOrderRepository.updateStatus(orderId, to)
        recordStatusTransition(orderId, from, to, reason)
    }

    private fun recordStatusTransition(orderId: UUID, from: PaymentStatus?, to: PaymentStatus, reason: String?) {
        statusHistoryRepository.save(
            PaymentStatusHistory(
                paymentOrderId = orderId,
                fromStatus = from,
                toStatus = to,
                reason = reason
            )
        )
    }
}

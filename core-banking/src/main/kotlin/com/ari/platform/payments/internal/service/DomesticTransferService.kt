package com.ari.platform.payments.internal.service

import com.ari.platform.compliance.internal.service.ComplianceService
import com.ari.platform.ledger.internal.model.AccountStatus
import com.ari.platform.ledger.internal.model.EntryDirection
import com.ari.platform.ledger.internal.model.PostingInstruction
import com.ari.platform.ledger.internal.model.TransactionType
import com.ari.platform.ledger.internal.service.AccountService
import com.ari.platform.ledger.internal.service.LedgerService
import com.ari.platform.payments.event.PaymentCompleted
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class DomesticTransferService(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusHistoryRepository: PaymentStatusHistoryRepository,
    private val ledgerService: LedgerService,
    private val accountService: AccountService,
    private val complianceService: ComplianceService,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Orchestrates a domestic P2P transfer between two accounts in the same currency.
     *
     * Steps:
     * 1. Idempotency check
     * 2. Validate sender and receiver accounts
     * 3. Create payment order
     * 4. Compliance check via ComplianceService (sanctions screening + transaction monitoring)
     * 5. Post double-entry via LedgerService (debit sender, credit receiver)
     * 6. Update status to completed
     */
    @Transactional
    fun execute(
        idempotencyKey: String,
        senderAccountId: UUID,
        receiverAccountId: UUID,
        amount: BigDecimal,
        currency: String,
        description: String? = null,
        initiatorId: UUID
    ): PaymentOrder {
        // Step 1: Idempotency check
        val existing = paymentOrderRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            log.info("Idempotent replay for domestic transfer key={}, orderId={}", idempotencyKey, existing.id)
            return existing
        }

        // Step 2: Validate accounts
        val senderAccount = accountService.getAccountById(senderAccountId)
        val receiverAccount = accountService.getAccountById(receiverAccountId)

        if (senderAccount.status != AccountStatus.ACTIVE) {
            throw BadRequestException("Sender account ${senderAccountId} is ${senderAccount.status.value}")
        }
        if (receiverAccount.status != AccountStatus.ACTIVE) {
            throw BadRequestException("Receiver account ${receiverAccountId} is ${receiverAccount.status.value}")
        }
        if (senderAccount.currency != currency) {
            throw BadRequestException("Sender account currency ${senderAccount.currency} does not match payment currency $currency")
        }
        if (receiverAccount.currency != currency) {
            throw BadRequestException("Receiver account currency ${receiverAccount.currency} does not match payment currency $currency")
        }
        if (senderAccountId == receiverAccountId) {
            throw BadRequestException("Sender and receiver accounts must be different")
        }
        if (amount <= BigDecimal.ZERO) {
            throw BadRequestException("Transfer amount must be positive")
        }

        // Step 3: Create payment order in INITIATED status
        val paymentOrder = paymentOrderRepository.save(
            PaymentOrder(
                idempotencyKey = idempotencyKey,
                type = PaymentType.DOMESTIC_P2P,
                status = PaymentStatus.INITIATED,
                senderAccountId = senderAccountId,
                receiverAccountId = receiverAccountId,
                amount = amount,
                currency = currency,
                description = description
            )
        )

        recordStatusTransition(paymentOrder.id, null, PaymentStatus.INITIATED, null)

        outboxPublisher.publish(
            PaymentInitiated(
                paymentOrderId = paymentOrder.id,
                paymentType = PaymentType.DOMESTIC_P2P.value,
                senderAccountId = senderAccountId,
                receiverAccountId = receiverAccountId,
                amount = amount,
                currency = currency
            )
        )

        // Step 4: Compliance check via real ComplianceService
        transitionStatus(paymentOrder.id, PaymentStatus.INITIATED, PaymentStatus.COMPLIANCE_CHECK, null)
        try {
            complianceService.checkPayment(
                senderId = senderAccount.userId,
                receiverId = receiverAccount.userId,
                amount = amount,
                currency = currency,
                transactionType = PaymentType.DOMESTIC_P2P.value
            )
        } catch (e: ComplianceRejectedException) {
            transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.FAILED, e.message)
            throw e
        }
        transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.PROCESSING, "Compliance check passed")

        // Step 5: Post double-entry via LedgerService
        transitionStatus(paymentOrder.id, PaymentStatus.PROCESSING, PaymentStatus.SETTLING, null)

        val ledgerTransaction = try {
            ledgerService.postEntries(
                idempotencyKey = "payment:${paymentOrder.id}",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(
                        accountId = senderAccountId,
                        direction = EntryDirection.DEBIT,
                        amount = amount,
                        currency = currency
                    ),
                    PostingInstruction(
                        accountId = receiverAccountId,
                        direction = EntryDirection.CREDIT,
                        amount = amount,
                        currency = currency
                    )
                ),
                referenceId = paymentOrder.id.toString(),
                metadata = mapOf(
                    "payment_type" to PaymentType.DOMESTIC_P2P.value,
                    "description" to (description ?: "")
                )
            )
        } catch (e: Exception) {
            transitionStatus(paymentOrder.id, PaymentStatus.SETTLING, PaymentStatus.FAILED, e.message)
            log.error("Domestic transfer failed for orderId={}: {}", paymentOrder.id, e.message)
            throw e
        }

        // Step 6: Update payment order with ledger transaction ID and mark completed
        paymentOrderRepository.updateLedgerTransactionId(paymentOrder.id, ledgerTransaction.id)
        transitionStatus(paymentOrder.id, PaymentStatus.SETTLING, PaymentStatus.COMPLETED, null)

        outboxPublisher.publish(
            PaymentCompleted(
                paymentOrderId = paymentOrder.id,
                paymentType = PaymentType.DOMESTIC_P2P.value,
                ledgerTransactionId = ledgerTransaction.id,
                amount = amount,
                currency = currency
            )
        )

        auditService.log(
            actorId = initiatorId,
            actorType = "user",
            action = "domestic_transfer",
            resourceType = "payment_order",
            resourceId = paymentOrder.id.toString(),
            details = mapOf(
                "sender_account_id" to senderAccountId.toString(),
                "receiver_account_id" to receiverAccountId.toString(),
                "amount" to amount.toPlainString(),
                "currency" to currency
            )
        )

        log.info(
            "Domestic transfer completed orderId={}, sender={}, receiver={}, amount={} {}",
            paymentOrder.id, senderAccountId, receiverAccountId, amount, currency
        )

        return paymentOrderRepository.findById(paymentOrder.id) ?: paymentOrder
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

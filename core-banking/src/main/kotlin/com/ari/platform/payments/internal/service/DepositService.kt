package com.ari.platform.payments.internal.service

import com.ari.platform.compliance.internal.service.ComplianceService
import com.ari.platform.ledger.internal.model.AccountStatus
import com.ari.platform.ledger.internal.model.AccountType
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
import com.ari.platform.payments.internal.repository.RailReference
import com.ari.platform.payments.internal.repository.RailReferenceRepository
import com.ari.platform.rails.internal.service.RailService
import com.ari.platform.shared.event.OutboxPublisher
import com.ari.platform.shared.exception.BadRequestException
import com.ari.platform.shared.exception.ComplianceRejectedException
import com.ari.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Orchestrates deposits from external bank accounts into user wallets.
 *
 * Deposit flow:
 * 1. Validate user account exists and is active
 * 2. Create payment order (INITIATED)
 * 3. Compliance check
 * 4. Submit to banking rail (FAST/EFT/SEPA) — the rail will confirm asynchronously
 * 5. On rail confirmation (via webhook/poller), credit user wallet via LedgerService
 */
@Service
class DepositService(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusHistoryRepository: PaymentStatusHistoryRepository,
    private val railReferenceRepository: RailReferenceRepository,
    private val ledgerService: LedgerService,
    private val accountService: AccountService,
    private val complianceService: ComplianceService,
    private val railService: RailService,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Initiate a deposit from an external bank account to a user's wallet.
     *
     * The deposit is submitted to the banking rail. The actual ledger credit
     * happens asynchronously when the rail confirms the payment.
     */
    @Transactional
    fun initiateDeposit(
        idempotencyKey: String,
        userId: UUID,
        accountId: UUID,
        amount: BigDecimal,
        currency: String,
        sourceIban: String,
        preferredRail: String? = null,
        description: String? = null
    ): PaymentOrder {
        // Idempotency check
        val existing = paymentOrderRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            log.info("Idempotent replay for deposit key={}, orderId={}", idempotencyKey, existing.id)
            return existing
        }

        // Validate account
        val account = accountService.getAccountById(accountId)
        if (account.userId != userId) {
            throw BadRequestException("Account does not belong to user")
        }
        if (account.status != AccountStatus.ACTIVE) {
            throw BadRequestException("Account is ${account.status.value}")
        }
        if (account.currency != currency) {
            throw BadRequestException("Account currency ${account.currency} does not match deposit currency $currency")
        }
        if (amount <= BigDecimal.ZERO) {
            throw BadRequestException("Deposit amount must be positive")
        }

        val destinationIban = account.iban
            ?: throw BadRequestException("Account does not have an IBAN assigned")

        // Get safeguarding account (deposits go through safeguarding)
        val safeguardingAccount = accountService.getOrCreateSystemAccount(currency, AccountType.SAFEGUARDING)

        // Create payment order
        val paymentOrder = paymentOrderRepository.save(
            PaymentOrder(
                idempotencyKey = idempotencyKey,
                type = PaymentType.DEPOSIT,
                status = PaymentStatus.INITIATED,
                senderAccountId = safeguardingAccount.id,  // external funds via safeguarding
                receiverAccountId = accountId,
                amount = amount,
                currency = currency,
                description = description,
                metadata = mapOf("source_iban" to sourceIban, "destination_iban" to destinationIban)
            )
        )

        recordStatusTransition(paymentOrder.id, null, PaymentStatus.INITIATED, null)

        outboxPublisher.publish(
            PaymentInitiated(
                paymentOrderId = paymentOrder.id,
                paymentType = PaymentType.DEPOSIT.value,
                senderAccountId = safeguardingAccount.id,
                receiverAccountId = accountId,
                amount = amount,
                currency = currency
            )
        )

        // Compliance check
        transitionStatus(paymentOrder.id, PaymentStatus.INITIATED, PaymentStatus.COMPLIANCE_CHECK, null)
        try {
            complianceService.checkPayment(
                senderId = userId,
                receiverId = userId,
                amount = amount,
                currency = currency,
                transactionType = PaymentType.DEPOSIT.value
            )
        } catch (e: ComplianceRejectedException) {
            transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.FAILED, e.message)
            throw e
        }

        // Submit to banking rail
        transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.PROCESSING, "Compliance passed")

        val railResult = railService.submitDeposit(
            paymentId = paymentOrder.id,
            sourceIban = sourceIban,
            destinationIban = destinationIban,
            amount = amount,
            currency = currency,
            preferredRail = preferredRail,
            description = description ?: "ARI deposit"
        )

        // Store rail reference for webhook resolution
        railReferenceRepository.save(
            RailReference(
                paymentOrderId = paymentOrder.id,
                provider = railResult.provider ?: railService.getAvailableRails().first(),
                externalReference = railResult.externalReference,
                status = "submitted"
            )
        )

        transitionStatus(paymentOrder.id, PaymentStatus.PROCESSING, PaymentStatus.SETTLING,
            "Submitted to rail: ${railResult.externalReference}")

        // Update payment order with rail info
        paymentOrderRepository.updateRail(paymentOrder.id, railResult.provider ?: railService.getAvailableRails().first())

        auditService.log(
            actorId = userId,
            actorType = "user",
            action = "initiate_deposit",
            resourceType = "payment_order",
            resourceId = paymentOrder.id.toString(),
            details = mapOf(
                "amount" to amount.toPlainString(),
                "currency" to currency,
                "source_iban" to sourceIban,
                "rail_ref" to railResult.externalReference
            )
        )

        log.info(
            "Deposit initiated orderId={}, amount={} {}, railRef={}",
            paymentOrder.id, amount, currency, railResult.externalReference
        )

        return paymentOrderRepository.findById(paymentOrder.id) ?: paymentOrder
    }

    /**
     * Complete a deposit after rail confirmation. Called by the RailEventListener.
     * Credits the user's wallet via the double-entry ledger.
     */
    @Transactional
    fun completeDeposit(paymentOrderId: UUID) {
        val order = paymentOrderRepository.findById(paymentOrderId)
            ?: throw BadRequestException("Payment order not found: $paymentOrderId")

        if (order.status != PaymentStatus.SETTLING) {
            log.warn("Deposit {} is in status {}, expected SETTLING", paymentOrderId, order.status)
            return
        }

        val ledgerTransaction = ledgerService.postEntries(
            idempotencyKey = "deposit:${order.id}",
            type = TransactionType.DEPOSIT,
            postings = listOf(
                PostingInstruction(
                    accountId = order.senderAccountId,  // safeguarding: debit (funds leave safeguarding)
                    direction = EntryDirection.DEBIT,
                    amount = order.amount,
                    currency = order.currency
                ),
                PostingInstruction(
                    accountId = order.receiverAccountId,  // user wallet: credit
                    direction = EntryDirection.CREDIT,
                    amount = order.amount,
                    currency = order.currency
                )
            ),
            referenceId = order.id.toString(),
            metadata = mapOf("payment_type" to "deposit")
        )

        paymentOrderRepository.updateLedgerTransactionId(order.id, ledgerTransaction.id)
        transitionStatus(order.id, PaymentStatus.SETTLING, PaymentStatus.COMPLETED, "Rail confirmed, ledger posted")

        outboxPublisher.publish(
            PaymentCompleted(
                paymentOrderId = order.id,
                paymentType = PaymentType.DEPOSIT.value,
                ledgerTransactionId = ledgerTransaction.id,
                amount = order.amount,
                currency = order.currency
            )
        )

        log.info("Deposit completed orderId={}, ledgerTxId={}", order.id, ledgerTransaction.id)
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

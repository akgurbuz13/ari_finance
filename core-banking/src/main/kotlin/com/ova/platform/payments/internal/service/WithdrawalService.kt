package com.ova.platform.payments.internal.service

import com.ova.platform.compliance.internal.service.ComplianceService
import com.ova.platform.ledger.internal.model.AccountStatus
import com.ova.platform.ledger.internal.model.AccountType
import com.ova.platform.ledger.internal.model.EntryDirection
import com.ova.platform.ledger.internal.model.PostingInstruction
import com.ova.platform.ledger.internal.model.TransactionType
import com.ova.platform.ledger.internal.service.AccountService
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.payments.event.PaymentCompleted
import com.ova.platform.payments.event.PaymentInitiated
import com.ova.platform.payments.internal.model.PaymentOrder
import com.ova.platform.payments.internal.model.PaymentStatus
import com.ova.platform.payments.internal.model.PaymentStatusHistory
import com.ova.platform.payments.internal.model.PaymentType
import com.ova.platform.payments.internal.repository.PaymentOrderRepository
import com.ova.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ova.platform.payments.internal.repository.RailReference
import com.ova.platform.payments.internal.repository.RailReferenceRepository
import com.ova.platform.rails.internal.service.RailService
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.ComplianceRejectedException
import com.ova.platform.shared.exception.InsufficientBalanceException
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Orchestrates withdrawals from user wallets to external bank accounts.
 *
 * Withdrawal flow:
 * 1. Validate user account and balance
 * 2. Create payment order (INITIATED)
 * 3. Compliance check
 * 4. Debit user wallet via LedgerService (immediate — funds held in safeguarding)
 * 5. Submit to banking rail (FAST/EFT/SEPA)
 * 6. On rail confirmation, mark completed. On failure, reverse ledger entry.
 */
@Service
class WithdrawalService(
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
     * Initiate a withdrawal from a user's wallet to an external bank account.
     *
     * The user's wallet is debited immediately (funds move to safeguarding).
     * The external payout is submitted to the banking rail asynchronously.
     */
    @Transactional
    fun initiateWithdrawal(
        idempotencyKey: String,
        userId: UUID,
        accountId: UUID,
        amount: BigDecimal,
        currency: String,
        destinationIban: String,
        preferredRail: String? = null,
        description: String? = null
    ): PaymentOrder {
        // Idempotency check
        val existing = paymentOrderRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            log.info("Idempotent replay for withdrawal key={}, orderId={}", idempotencyKey, existing.id)
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
            throw BadRequestException("Account currency ${account.currency} does not match withdrawal currency $currency")
        }
        if (amount <= BigDecimal.ZERO) {
            throw BadRequestException("Withdrawal amount must be positive")
        }

        val sourceIban = account.iban
            ?: throw BadRequestException("Account does not have an IBAN assigned")

        // Check balance
        val balance = accountService.getBalance(accountId)
        if (balance < amount) {
            throw InsufficientBalanceException("Insufficient balance: $balance $currency (need $amount)")
        }

        // Get safeguarding account (withdrawals go through safeguarding)
        val safeguardingAccount = accountService.getOrCreateSystemAccount(currency, AccountType.SAFEGUARDING)

        // Create payment order
        val paymentOrder = paymentOrderRepository.save(
            PaymentOrder(
                idempotencyKey = idempotencyKey,
                type = PaymentType.WITHDRAWAL,
                status = PaymentStatus.INITIATED,
                senderAccountId = accountId,
                receiverAccountId = safeguardingAccount.id,  // funds move to safeguarding before payout
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
                paymentType = PaymentType.WITHDRAWAL.value,
                senderAccountId = accountId,
                receiverAccountId = safeguardingAccount.id,
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
                transactionType = PaymentType.WITHDRAWAL.value
            )
        } catch (e: ComplianceRejectedException) {
            transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.FAILED, e.message)
            throw e
        }

        // Debit user wallet immediately (move funds to safeguarding)
        transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.PROCESSING, "Compliance passed")

        val ledgerTransaction = try {
            ledgerService.postEntries(
                idempotencyKey = "withdrawal:${paymentOrder.id}",
                type = TransactionType.WITHDRAWAL,
                postings = listOf(
                    PostingInstruction(
                        accountId = accountId,  // user wallet: debit
                        direction = EntryDirection.DEBIT,
                        amount = amount,
                        currency = currency
                    ),
                    PostingInstruction(
                        accountId = safeguardingAccount.id,  // safeguarding: credit (holds funds)
                        direction = EntryDirection.CREDIT,
                        amount = amount,
                        currency = currency
                    )
                ),
                referenceId = paymentOrder.id.toString(),
                metadata = mapOf(
                    "payment_type" to "withdrawal",
                    "destination_iban" to destinationIban
                )
            )
        } catch (e: Exception) {
            transitionStatus(paymentOrder.id, PaymentStatus.PROCESSING, PaymentStatus.FAILED, e.message)
            throw e
        }

        paymentOrderRepository.updateLedgerTransactionId(paymentOrder.id, ledgerTransaction.id)

        // Submit to banking rail
        transitionStatus(paymentOrder.id, PaymentStatus.PROCESSING, PaymentStatus.SETTLING, "Ledger debited, submitting to rail")

        val railResult = railService.submitWithdrawal(
            paymentId = paymentOrder.id,
            sourceIban = sourceIban,
            destinationIban = destinationIban,
            amount = amount,
            currency = currency,
            preferredRail = preferredRail,
            description = description ?: "Ova withdrawal"
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

        // Update payment order with rail info
        paymentOrderRepository.updateRail(paymentOrder.id, railResult.provider ?: railService.getAvailableRails().first())

        auditService.log(
            actorId = userId,
            actorType = "user",
            action = "initiate_withdrawal",
            resourceType = "payment_order",
            resourceId = paymentOrder.id.toString(),
            details = mapOf(
                "amount" to amount.toPlainString(),
                "currency" to currency,
                "destination_iban" to destinationIban,
                "rail_ref" to railResult.externalReference
            )
        )

        log.info(
            "Withdrawal initiated orderId={}, amount={} {}, railRef={}",
            paymentOrder.id, amount, currency, railResult.externalReference
        )

        return paymentOrderRepository.findById(paymentOrder.id) ?: paymentOrder
    }

    /**
     * Complete a withdrawal after rail confirmation. Called by the RailEventListener.
     */
    @Transactional
    fun completeWithdrawal(paymentOrderId: UUID) {
        val order = paymentOrderRepository.findById(paymentOrderId)
            ?: throw BadRequestException("Payment order not found: $paymentOrderId")

        if (order.status != PaymentStatus.SETTLING) {
            log.warn("Withdrawal {} is in status {}, expected SETTLING", paymentOrderId, order.status)
            return
        }

        transitionStatus(order.id, PaymentStatus.SETTLING, PaymentStatus.COMPLETED, "Rail confirmed")

        outboxPublisher.publish(
            PaymentCompleted(
                paymentOrderId = order.id,
                paymentType = PaymentType.WITHDRAWAL.value,
                ledgerTransactionId = order.ledgerTransactionId!!,
                amount = order.amount,
                currency = order.currency
            )
        )

        log.info("Withdrawal completed orderId={}", order.id)
    }

    /**
     * Reverse a failed withdrawal. Called when the rail rejects the payout.
     * Moves funds back from safeguarding to the user's wallet.
     */
    @Transactional
    fun reverseWithdrawal(paymentOrderId: UUID, reason: String) {
        val order = paymentOrderRepository.findById(paymentOrderId)
            ?: throw BadRequestException("Payment order not found: $paymentOrderId")

        if (order.status != PaymentStatus.SETTLING) {
            log.warn("Withdrawal {} is in status {}, cannot reverse", paymentOrderId, order.status)
            return
        }

        // Reverse: credit user wallet, debit safeguarding
        ledgerService.postEntries(
            idempotencyKey = "withdrawal-reversal:${order.id}",
            type = TransactionType.WITHDRAWAL,
            postings = listOf(
                PostingInstruction(
                    accountId = order.senderAccountId,  // user wallet: credit back
                    direction = EntryDirection.CREDIT,
                    amount = order.amount,
                    currency = order.currency
                ),
                PostingInstruction(
                    accountId = order.receiverAccountId,  // safeguarding: debit back
                    direction = EntryDirection.DEBIT,
                    amount = order.amount,
                    currency = order.currency
                )
            ),
            referenceId = "reversal:${order.id}",
            metadata = mapOf("payment_type" to "withdrawal_reversal", "reason" to reason)
        )

        transitionStatus(order.id, PaymentStatus.SETTLING, PaymentStatus.REVERSED, reason)

        log.info("Withdrawal reversed orderId={}, reason={}", order.id, reason)
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

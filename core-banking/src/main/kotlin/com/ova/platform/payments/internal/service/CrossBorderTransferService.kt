package com.ova.platform.payments.internal.service

import com.ova.platform.ledger.internal.model.AccountType
import com.ova.platform.ledger.internal.model.AccountStatus
import com.ova.platform.ledger.internal.model.EntryDirection
import com.ova.platform.ledger.internal.model.PostingInstruction
import com.ova.platform.ledger.internal.model.TransactionType
import com.ova.platform.ledger.internal.service.AccountService
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.payments.event.BurnRequested
import com.ova.platform.payments.event.MintRequested
import com.ova.platform.payments.event.PaymentInitiated
import com.ova.platform.payments.internal.model.FxQuote
import com.ova.platform.payments.internal.model.PaymentOrder
import com.ova.platform.payments.internal.model.PaymentStatus
import com.ova.platform.payments.internal.model.PaymentStatusHistory
import com.ova.platform.payments.internal.model.PaymentType
import com.ova.platform.payments.internal.repository.FxQuoteRepository
import com.ova.platform.payments.internal.repository.PaymentOrderRepository
import com.ova.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.ComplianceRejectedException
import com.ova.platform.shared.exception.QuoteExpiredException
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class CrossBorderTransferService(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val statusHistoryRepository: PaymentStatusHistoryRepository,
    private val fxQuoteRepository: FxQuoteRepository,
    private val ledgerService: LedgerService,
    private val accountService: AccountService,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Saga orchestrator for cross-border transfers (e.g. TRY -> EUR).
     *
     * Steps:
     * 1. Idempotency check
     * 2. Validate accounts and FX quote
     * 3. Compliance check (stub)
     * 4. Ledger postings:
     *    a. Debit sender TRY wallet
     *    b. Credit system float TRY (source side receives funds)
     *    c. Move source funds from float to safeguarding source account
     *    d. Move target funds from safeguarding target account to float target account
     *    e. Debit system float EUR (target side sends funds)
     *    f. Credit receiver EUR wallet
     *    g. Fee entries: debit sender TRY, credit fee revenue TRY
     * 5. Publish MintRequested (credit side) and BurnRequested (debit side) to outbox
     * 6. Keep payment in settling state until both on-chain legs are confirmed
     */
    @Transactional
    fun execute(
        idempotencyKey: String,
        senderAccountId: UUID,
        receiverAccountId: UUID,
        fxQuoteId: UUID,
        description: String? = null,
        initiatorId: UUID
    ): PaymentOrder {
        // Step 1: Idempotency check
        val existing = paymentOrderRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            log.info("Idempotent replay for cross-border transfer key={}, orderId={}", idempotencyKey, existing.id)
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
        if (senderAccount.currency == receiverAccount.currency) {
            throw BadRequestException("FX cross-border transfer requires different currencies. Use same-currency cross-border instead.")
        }

        // Validate FX quote
        val fxQuote = fxQuoteRepository.findById(fxQuoteId)
            ?: throw BadRequestException("FX quote not found: $fxQuoteId")

        validateFxQuote(fxQuote, senderAccount.currency, receiverAccount.currency)

        // Step 3: Create payment order
        val paymentOrder = paymentOrderRepository.save(
            PaymentOrder(
                idempotencyKey = idempotencyKey,
                type = PaymentType.CROSS_BORDER,
                status = PaymentStatus.INITIATED,
                senderAccountId = senderAccountId,
                receiverAccountId = receiverAccountId,
                amount = fxQuote.sourceAmount,
                currency = senderAccount.currency,
                feeAmount = fxQuote.feeAmount,
                feeCurrency = fxQuote.feeCurrency,
                fxQuoteId = fxQuoteId,
                description = description,
                metadata = mapOf(
                    "exchange_rate" to fxQuote.exchangeRate.toPlainString(),
                    "source_currency" to fxQuote.sourceCurrency,
                    "target_currency" to fxQuote.targetCurrency,
                    "target_amount" to fxQuote.targetAmount.toPlainString()
                )
            )
        )

        recordStatusTransition(paymentOrder.id, null, PaymentStatus.INITIATED, null)

        outboxPublisher.publish(
            PaymentInitiated(
                paymentOrderId = paymentOrder.id,
                paymentType = PaymentType.CROSS_BORDER.value,
                senderAccountId = senderAccountId,
                receiverAccountId = receiverAccountId,
                amount = fxQuote.sourceAmount,
                currency = senderAccount.currency
            )
        )

        // Step 4: Compliance check
        transitionStatus(paymentOrder.id, PaymentStatus.INITIATED, PaymentStatus.COMPLIANCE_CHECK, null)
        performComplianceCheck(paymentOrder, fxQuote)
        transitionStatus(paymentOrder.id, PaymentStatus.COMPLIANCE_CHECK, PaymentStatus.PROCESSING, "Compliance check passed")

        // Step 5: Ledger postings
        // TODO [PRODUCTION]: When deploying multi-region, replace direct DB writes for
        // foreign-region accounts with cross-region API calls. The receiver's ledger
        // credit (Leg 3) should be posted via the receiver's regional core-banking instance.
        // See docs/adr/001-multi-region-data-residency.md
        transitionStatus(paymentOrder.id, PaymentStatus.PROCESSING, PaymentStatus.SETTLING, null)

        try {
            // Mark FX quote as used
            fxQuoteRepository.markAsUsed(fxQuoteId)

            // Get or create system accounts
            val systemFloatSource = accountService.getOrCreateSystemAccount(senderAccount.currency, AccountType.SYSTEM_FLOAT)
            val systemFloatTarget = accountService.getOrCreateSystemAccount(receiverAccount.currency, AccountType.SYSTEM_FLOAT)
            val safeguardingSource = accountService.getOrCreateSystemAccount(senderAccount.currency, AccountType.SAFEGUARDING)
            val safeguardingTarget = accountService.getOrCreateSystemAccount(receiverAccount.currency, AccountType.SAFEGUARDING)
            val feeRevenueAccount = accountService.getOrCreateSystemAccount(fxQuote.feeCurrency, AccountType.FEE_REVENUE)

            // Main transfer postings:
            // Leg 1: Debit sender TRY, Credit system float TRY (source side)
            // Leg 2: Debit system float TRY, Credit system float EUR via FX (conversion)
            // Leg 3: Debit system float EUR, Credit receiver EUR (target side)
            val mainPostings = listOf(
                // Leg 1: Source currency movement - sender to system float
                PostingInstruction(
                    accountId = senderAccountId,
                    direction = EntryDirection.DEBIT,
                    amount = fxQuote.sourceAmount,
                    currency = senderAccount.currency
                ),
                PostingInstruction(
                    accountId = systemFloatSource.id,
                    direction = EntryDirection.CREDIT,
                    amount = fxQuote.sourceAmount,
                    currency = senderAccount.currency
                )
            )

            val mainTransaction = ledgerService.postEntries(
                idempotencyKey = "payment:${paymentOrder.id}:main",
                type = TransactionType.CROSS_BORDER,
                postings = mainPostings,
                referenceId = paymentOrder.id.toString(),
                metadata = mapOf(
                    "payment_type" to PaymentType.CROSS_BORDER.value,
                    "leg" to "source_debit"
                )
            )

            // Leg 2: FX bookkeeping and safeguarding movements
            val fxPostings = listOf(
                // Source side: move captured funds to source safeguarding account
                PostingInstruction(
                    accountId = systemFloatSource.id,
                    direction = EntryDirection.DEBIT,
                    amount = fxQuote.sourceAmount,
                    currency = senderAccount.currency
                ),
                PostingInstruction(
                    accountId = safeguardingSource.id,
                    direction = EntryDirection.CREDIT,
                    amount = fxQuote.sourceAmount,
                    currency = senderAccount.currency
                ),
                // Target side: release prefunded target currency from safeguarding into float
                PostingInstruction(
                    accountId = safeguardingTarget.id,
                    direction = EntryDirection.DEBIT,
                    amount = fxQuote.targetAmount,
                    currency = receiverAccount.currency
                ),
                PostingInstruction(
                    accountId = systemFloatTarget.id,
                    direction = EntryDirection.CREDIT,
                    amount = fxQuote.targetAmount,
                    currency = receiverAccount.currency
                )
            )

            ledgerService.postEntries(
                idempotencyKey = "payment:${paymentOrder.id}:fx",
                type = TransactionType.FX_CONVERSION,
                postings = fxPostings,
                referenceId = paymentOrder.id.toString(),
                metadata = mapOf(
                    "exchange_rate" to fxQuote.exchangeRate.toPlainString(),
                    "source_currency" to fxQuote.sourceCurrency,
                    "target_currency" to fxQuote.targetCurrency
                )
            )

            // Leg 3: Target currency movement - system float to receiver
            val targetPostings = listOf(
                PostingInstruction(
                    accountId = systemFloatTarget.id,
                    direction = EntryDirection.DEBIT,
                    amount = fxQuote.targetAmount,
                    currency = receiverAccount.currency
                ),
                PostingInstruction(
                    accountId = receiverAccountId,
                    direction = EntryDirection.CREDIT,
                    amount = fxQuote.targetAmount,
                    currency = receiverAccount.currency
                )
            )

            ledgerService.postEntries(
                idempotencyKey = "payment:${paymentOrder.id}:target",
                type = TransactionType.CROSS_BORDER,
                postings = targetPostings,
                referenceId = paymentOrder.id.toString(),
                metadata = mapOf(
                    "payment_type" to PaymentType.CROSS_BORDER.value,
                    "leg" to "target_credit"
                )
            )

            // Fee entries: debit sender, credit fee revenue account
            if (fxQuote.feeAmount > BigDecimal.ZERO) {
                val feePostings = listOf(
                    PostingInstruction(
                        accountId = senderAccountId,
                        direction = EntryDirection.DEBIT,
                        amount = fxQuote.feeAmount,
                        currency = fxQuote.feeCurrency
                    ),
                    PostingInstruction(
                        accountId = feeRevenueAccount.id,
                        direction = EntryDirection.CREDIT,
                        amount = fxQuote.feeAmount,
                        currency = fxQuote.feeCurrency
                    )
                )

                ledgerService.postEntries(
                    idempotencyKey = "payment:${paymentOrder.id}:fee",
                    type = TransactionType.FEE,
                    postings = feePostings,
                    referenceId = paymentOrder.id.toString(),
                    metadata = mapOf(
                        "payment_type" to PaymentType.CROSS_BORDER.value,
                        "fee_type" to "fx_fee"
                    )
                )
            }

            // Update payment order with primary ledger transaction ID
            paymentOrderRepository.updateLedgerTransactionId(paymentOrder.id, mainTransaction.id)

            // Step 6: Publish mint/burn events to outbox for stablecoin rail
            outboxPublisher.publish(
                BurnRequested(
                    paymentOrderId = paymentOrder.id,
                    sourceAccountId = senderAccountId,
                    amount = fxQuote.sourceAmount,
                    currency = senderAccount.currency,
                    region = regionForCurrency(senderAccount.currency)
                )
            )

            outboxPublisher.publish(
                MintRequested(
                    paymentOrderId = paymentOrder.id,
                    targetAccountId = receiverAccountId,
                    amount = fxQuote.targetAmount,
                    currency = receiverAccount.currency,
                    region = regionForCurrency(receiverAccount.currency)
                )
            )

            // Step 7: Payment remains in SETTLING until both burn and mint confirmations arrive.

            auditService.log(
                actorId = initiatorId,
                actorType = "user",
                action = "cross_border_transfer",
                resourceType = "payment_order",
                resourceId = paymentOrder.id.toString(),
                details = mapOf(
                    "sender_account_id" to senderAccountId.toString(),
                    "receiver_account_id" to receiverAccountId.toString(),
                    "source_amount" to fxQuote.sourceAmount.toPlainString(),
                    "source_currency" to fxQuote.sourceCurrency,
                    "target_amount" to fxQuote.targetAmount.toPlainString(),
                    "target_currency" to fxQuote.targetCurrency,
                    "exchange_rate" to fxQuote.exchangeRate.toPlainString(),
                    "fee_amount" to fxQuote.feeAmount.toPlainString()
                )
            )

            log.info(
                "Cross-border transfer completed orderId={}, {} {} -> {} {}, rate={}",
                paymentOrder.id,
                fxQuote.sourceAmount, fxQuote.sourceCurrency,
                fxQuote.targetAmount, fxQuote.targetCurrency,
                fxQuote.exchangeRate
            )

            return paymentOrderRepository.findById(paymentOrder.id) ?: paymentOrder

        } catch (e: Exception) {
            transitionStatus(paymentOrder.id, PaymentStatus.SETTLING, PaymentStatus.FAILED, e.message)
            log.error("Cross-border transfer failed for orderId={}: {}", paymentOrder.id, e.message)
            throw e
        }
    }

    private fun regionForCurrency(currency: String): String = when (currency) {
        "TRY" -> "TR"
        "EUR" -> "EU"
        else -> throw BadRequestException("Unsupported currency for cross-border: $currency")
    }

    private fun validateFxQuote(quote: FxQuote, sourceCurrency: String, targetCurrency: String) {
        if (quote.used) {
            throw BadRequestException("FX quote ${quote.id} has already been used")
        }
        if (quote.expiresAt.isBefore(Instant.now())) {
            throw QuoteExpiredException()
        }
        if (quote.sourceCurrency != sourceCurrency) {
            throw BadRequestException(
                "FX quote source currency ${quote.sourceCurrency} does not match sender account currency $sourceCurrency"
            )
        }
        if (quote.targetCurrency != targetCurrency) {
            throw BadRequestException(
                "FX quote target currency ${quote.targetCurrency} does not match receiver account currency $targetCurrency"
            )
        }
    }

    /**
     * Stub compliance check for cross-border transfers.
     * In production, this would call external KYC/AML/sanctions screening services.
     */
    private fun performComplianceCheck(order: PaymentOrder, quote: FxQuote) {
        val crossBorderLimit = BigDecimal("500000.00")
        if (quote.sourceAmount > crossBorderLimit) {
            transitionStatus(
                order.id,
                PaymentStatus.COMPLIANCE_CHECK,
                PaymentStatus.FAILED,
                "Amount exceeds cross-border transfer limit"
            )
            throw ComplianceRejectedException(
                "Amount exceeds cross-border transfer limit of $crossBorderLimit ${quote.sourceCurrency}"
            )
        }
        log.debug("Cross-border compliance check passed for orderId={}", order.id)
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

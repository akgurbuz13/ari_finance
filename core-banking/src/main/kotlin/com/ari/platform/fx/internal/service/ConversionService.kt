package com.ari.platform.fx.internal.service

import com.ari.platform.fx.event.ConversionExecuted
import com.ari.platform.ledger.internal.model.EntryDirection
import com.ari.platform.ledger.internal.model.PostingInstruction
import com.ari.platform.ledger.internal.model.TransactionType
import com.ari.platform.ledger.internal.service.LedgerService
import com.ari.platform.shared.event.OutboxPublisher
import com.ari.platform.shared.exception.BadRequestException
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Executes FX conversions by consuming a valid quote and posting the corresponding
 * double-entry ledger entries.
 *
 * A conversion debits the source currency account and credits the target currency account,
 * while also recording the spread as fee revenue.
 */
@Service
class ConversionService(
    private val quoteService: QuoteService,
    private val ledgerService: LedgerService,
    private val jdbcTemplate: JdbcTemplate,
    private val outboxPublisher: OutboxPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Execute an FX conversion using a previously created quote.
     *
     * Steps:
     * 1. Validate and lock the quote (must be active and not expired)
     * 2. Resolve the user's source and target currency accounts
     * 3. Post balanced ledger entries (debit source, credit target)
     * 4. Mark the quote as consumed
     * 5. Publish a [ConversionExecuted] domain event
     *
     * @param quoteId the ID of the quote to execute
     * @param userId the ID of the user performing the conversion
     * @param sourceAccountId the user's source currency account
     * @param targetAccountId the user's target currency account
     * @return a [ConversionResult] with the transaction details
     */
    @Transactional
    fun executeConversion(
        quoteId: UUID,
        userId: UUID,
        sourceAccountId: UUID,
        targetAccountId: UUID
    ): ConversionResult {
        // Step 1: Validate the quote
        val quote = quoteService.validateQuote(quoteId)

        log.info(
            "Executing FX conversion quoteId={}, userId={}, {} {} -> {} {}",
            quoteId, userId,
            quote.sourceAmount, quote.sourceCurrency,
            quote.targetAmount, quote.targetCurrency
        )

        // Step 2: Verify account currencies match the quote
        validateAccountCurrencies(sourceAccountId, targetAccountId, quote)

        // Step 3: Post ledger entries
        // The conversion uses two separate posting sets (one per currency) since
        // the ledger validates debits == credits per currency.
        val idempotencyKey = "fx-conversion-$quoteId"

        // Resolve system float accounts for each currency
        val sourceFloatAccountId = resolveSystemFloatAccount(quote.sourceCurrency)
        val targetFloatAccountId = resolveSystemFloatAccount(quote.targetCurrency)

        val postings = listOf(
            // Source currency: debit user, credit system float
            PostingInstruction(
                accountId = sourceAccountId,
                direction = EntryDirection.DEBIT,
                amount = quote.sourceAmount,
                currency = quote.sourceCurrency
            ),
            PostingInstruction(
                accountId = sourceFloatAccountId,
                direction = EntryDirection.CREDIT,
                amount = quote.sourceAmount,
                currency = quote.sourceCurrency
            ),
            // Target currency: debit system float, credit user
            PostingInstruction(
                accountId = targetFloatAccountId,
                direction = EntryDirection.DEBIT,
                amount = quote.targetAmount,
                currency = quote.targetCurrency
            ),
            PostingInstruction(
                accountId = targetAccountId,
                direction = EntryDirection.CREDIT,
                amount = quote.targetAmount,
                currency = quote.targetCurrency
            )
        )

        val transaction = ledgerService.postEntries(
            idempotencyKey = idempotencyKey,
            type = TransactionType.FX_CONVERSION,
            postings = postings,
            referenceId = quoteId.toString(),
            metadata = mapOf(
                "quoteId" to quoteId.toString(),
                "sourceCurrency" to quote.sourceCurrency,
                "targetCurrency" to quote.targetCurrency,
                "customerRate" to quote.customerRate.toPlainString(),
                "spread" to quote.spread.toPlainString()
            )
        )

        // Step 4: Consume the quote
        quoteService.consumeQuote(quoteId)

        // Step 5: Record the conversion
        val conversionId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO payments.fx_conversions
                (id, quote_id, transaction_id, user_id, source_account_id, target_account_id,
                 source_currency, target_currency, source_amount, target_amount, customer_rate)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            conversionId,
            quoteId,
            transaction.id,
            userId,
            sourceAccountId,
            targetAccountId,
            quote.sourceCurrency,
            quote.targetCurrency,
            quote.sourceAmount,
            quote.targetAmount,
            quote.customerRate
        )

        // Step 6: Publish event
        outboxPublisher.publish(
            ConversionExecuted(
                conversionId = conversionId,
                quoteId = quoteId,
                transactionId = transaction.id,
                userId = userId,
                sourceCurrency = quote.sourceCurrency,
                targetCurrency = quote.targetCurrency,
                sourceAmount = quote.sourceAmount,
                targetAmount = quote.targetAmount,
                customerRate = quote.customerRate
            )
        )

        log.info(
            "FX conversion completed conversionId={}, txId={}, quoteId={}",
            conversionId, transaction.id, quoteId
        )

        return ConversionResult(
            conversionId = conversionId,
            transactionId = transaction.id,
            quoteId = quoteId,
            sourceCurrency = quote.sourceCurrency,
            targetCurrency = quote.targetCurrency,
            sourceAmount = quote.sourceAmount,
            targetAmount = quote.targetAmount,
            customerRate = quote.customerRate,
            executedAt = Instant.now()
        )
    }

    /**
     * Verify that the provided accounts have currencies matching the quote.
     */
    private fun validateAccountCurrencies(
        sourceAccountId: UUID,
        targetAccountId: UUID,
        quote: FxQuote
    ) {
        val sourceCurrency = getAccountCurrency(sourceAccountId)
        val targetCurrency = getAccountCurrency(targetAccountId)

        if (sourceCurrency != quote.sourceCurrency) {
            throw BadRequestException(
                "Source account currency ($sourceCurrency) does not match quote source currency (${quote.sourceCurrency})"
            )
        }
        if (targetCurrency != quote.targetCurrency) {
            throw BadRequestException(
                "Target account currency ($targetCurrency) does not match quote target currency (${quote.targetCurrency})"
            )
        }
    }

    private fun getAccountCurrency(accountId: UUID): String {
        return jdbcTemplate.queryForObject(
            "SELECT currency FROM ledger.accounts WHERE id = ?",
            String::class.java,
            accountId
        ) ?: throw BadRequestException("Account not found: $accountId")
    }

    /**
     * Resolve the system float account for a given currency.
     * System float accounts are used as the counterparty in FX conversions.
     */
    private fun resolveSystemFloatAccount(currency: String): UUID {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM ledger.accounts WHERE account_type = 'system_float' AND currency = ? LIMIT 1",
            UUID::class.java,
            currency
        ) ?: throw BadRequestException("System float account not found for currency: $currency")
    }
}

data class ConversionResult(
    val conversionId: UUID,
    val transactionId: UUID,
    val quoteId: UUID,
    val sourceCurrency: String,
    val targetCurrency: String,
    val sourceAmount: BigDecimal,
    val targetAmount: BigDecimal,
    val customerRate: BigDecimal,
    val executedAt: Instant
)

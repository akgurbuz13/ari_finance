package com.ari.platform.fx.internal.service

import com.ari.platform.fx.event.QuoteCreated
import com.ari.platform.fx.internal.provider.FxRateProvider
import com.ari.platform.shared.event.OutboxPublisher
import com.ari.platform.shared.exception.BadRequestException
import com.ari.platform.shared.exception.NotFoundException
import com.ari.platform.shared.exception.QuoteExpiredException
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Creates and validates time-limited FX quotes.
 *
 * Quotes have a 30-second TTL and include a spread applied on top of the mid-market rate.
 * Quotes are stored in the payments.fx_quotes table and can be consumed exactly once
 * by [ConversionService].
 */
@Service
class QuoteService(
    private val fxRateProvider: FxRateProvider,
    private val jdbcTemplate: JdbcTemplate,
    private val outboxPublisher: OutboxPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Quote validity window in seconds. */
        const val QUOTE_TTL_SECONDS: Long = 30

        /** Spread applied on top of mid-market rate (0.3% to 0.5% depending on pair). */
        private val DEFAULT_SPREAD = BigDecimal("0.003")
        private val CROSS_BORDER_SPREAD = BigDecimal("0.005")
    }

    /**
     * Create a new FX quote for converting [sourceAmount] of [sourceCurrency] to [targetCurrency].
     *
     * The quote includes:
     * - Mid-market rate from the rate provider
     * - Spread-adjusted customer rate
     * - Calculated target amount
     * - 30-second expiry
     */
    @Transactional
    fun createQuote(
        sourceCurrency: String,
        targetCurrency: String,
        sourceAmount: BigDecimal
    ): FxQuote {
        if (sourceCurrency == targetCurrency) {
            throw BadRequestException("Source and target currencies must be different")
        }
        if (sourceAmount <= BigDecimal.ZERO) {
            throw BadRequestException("Source amount must be positive")
        }

        val midMarketRate = fxRateProvider.getRate(sourceCurrency, targetCurrency)

        // Determine spread based on currency pair
        val spread = resolveSpread(sourceCurrency, targetCurrency)

        // Apply spread: customer gets a slightly worse rate
        val customerRate = midMarketRate.rate
            .multiply(BigDecimal.ONE.subtract(spread))
            .setScale(8, RoundingMode.HALF_UP)

        val targetAmount = sourceAmount
            .multiply(customerRate)
            .setScale(2, RoundingMode.HALF_UP)

        val quoteId = UUID.randomUUID()
        val expiresAt = Instant.now().plusSeconds(QUOTE_TTL_SECONDS)

        // Persist the quote
        jdbcTemplate.update(
            """
            INSERT INTO payments.fx_quotes
                (id, source_currency, target_currency, source_amount, target_amount,
                 rate, inverse_rate, exchange_rate, mid_market_rate, customer_rate,
                 spread, fee_amount, fee_currency, expires_at, status, used)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', false)
            """,
            quoteId,
            sourceCurrency,
            targetCurrency,
            sourceAmount,
            targetAmount,
            customerRate,
            if (customerRate.signum() == 0) BigDecimal.ZERO
            else BigDecimal.ONE.divide(customerRate, 10, RoundingMode.HALF_UP),
            customerRate,
            midMarketRate.rate,
            customerRate,
            spread,
            BigDecimal.ZERO,
            targetCurrency,
            java.sql.Timestamp.from(expiresAt)
        )

        val quote = FxQuote(
            id = quoteId,
            sourceCurrency = sourceCurrency,
            targetCurrency = targetCurrency,
            sourceAmount = sourceAmount,
            targetAmount = targetAmount,
            midMarketRate = midMarketRate.rate,
            customerRate = customerRate,
            spread = spread,
            expiresAt = expiresAt,
            status = QuoteStatus.ACTIVE
        )

        outboxPublisher.publish(
            QuoteCreated(
                quoteId = quoteId,
                sourceCurrency = sourceCurrency,
                targetCurrency = targetCurrency,
                sourceAmount = sourceAmount,
                targetAmount = targetAmount,
                customerRate = customerRate
            )
        )

        log.info(
            "Created FX quote quoteId={}, {}/{}, rate={}, amount={}->{}",
            quoteId, sourceCurrency, targetCurrency, customerRate, sourceAmount, targetAmount
        )

        return quote
    }

    /**
     * Validate that a quote exists, is active, and has not expired.
     * Returns the quote if valid.
     *
     * @throws NotFoundException if the quote does not exist
     * @throws QuoteExpiredException if the quote has expired or been consumed
     */
    fun validateQuote(quoteId: UUID): FxQuote {
        val quote = findQuoteById(quoteId)
            ?: throw NotFoundException("FxQuote", quoteId.toString())

        if (quote.status != QuoteStatus.ACTIVE) {
            throw QuoteExpiredException()
        }

        if (Instant.now().isAfter(quote.expiresAt)) {
            // Mark as expired in the database
            jdbcTemplate.update(
                "UPDATE payments.fx_quotes SET status = 'expired' WHERE id = ?",
                quoteId
            )
            throw QuoteExpiredException()
        }

        return quote
    }

    /**
     * Mark a quote as consumed. Called by [ConversionService] after successful conversion.
     */
    @Transactional
    fun consumeQuote(quoteId: UUID) {
        jdbcTemplate.update(
            "UPDATE payments.fx_quotes SET status = 'consumed', consumed_at = NOW(), used = true WHERE id = ?",
            quoteId
        )
        log.info("Quote consumed quoteId={}", quoteId)
    }

    private fun findQuoteById(quoteId: UUID): FxQuote? {
        val results = jdbcTemplate.query(
            """
            SELECT id, source_currency, target_currency, source_amount, target_amount,
                   rate, mid_market_rate, customer_rate, spread, expires_at, status, used, created_at
            FROM payments.fx_quotes WHERE id = ?
            """,
            { rs: ResultSet, _: Int -> mapQuote(rs) },
            quoteId
        )
        return results.firstOrNull()
    }

    private fun mapQuote(rs: ResultSet): FxQuote {
        val customerRate = rs.getBigDecimal("customer_rate")
            ?: rs.getBigDecimal("rate")
            ?: throw IllegalStateException("Quote ${rs.getString("id")} has no customer rate")
        val spread = rs.getBigDecimal("spread") ?: BigDecimal.ZERO
        val midMarketRate = rs.getBigDecimal("mid_market_rate")
            ?: if (BigDecimal.ONE.subtract(spread).compareTo(BigDecimal.ZERO) == 0) customerRate
            else customerRate.divide(BigDecimal.ONE.subtract(spread), 8, RoundingMode.HALF_UP)

        val statusValue = rs.getString("status")
        val status = if (!statusValue.isNullOrBlank()) {
            QuoteStatus.fromValue(statusValue)
        } else if (rs.getBoolean("used")) {
            QuoteStatus.CONSUMED
        } else if (rs.getTimestamp("expires_at").toInstant().isBefore(Instant.now())) {
            QuoteStatus.EXPIRED
        } else {
            QuoteStatus.ACTIVE
        }

        return FxQuote(
            id = UUID.fromString(rs.getString("id")),
            sourceCurrency = rs.getString("source_currency"),
            targetCurrency = rs.getString("target_currency"),
            sourceAmount = rs.getBigDecimal("source_amount"),
            targetAmount = rs.getBigDecimal("target_amount"),
            midMarketRate = midMarketRate,
            customerRate = customerRate,
            spread = spread,
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            status = status,
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private fun resolveSpread(sourceCurrency: String, targetCurrency: String): BigDecimal {
        // Higher spread for cross-border pairs
        return if (sourceCurrency == "TRY" && targetCurrency == "EUR" ||
            sourceCurrency == "EUR" && targetCurrency == "TRY"
        ) {
            CROSS_BORDER_SPREAD
        } else {
            DEFAULT_SPREAD
        }
    }
}

// ── Quote models ───────────────────────────────────────────────────────────────

data class FxQuote(
    val id: UUID,
    val sourceCurrency: String,
    val targetCurrency: String,
    val sourceAmount: BigDecimal,
    val targetAmount: BigDecimal,
    val midMarketRate: BigDecimal,
    val customerRate: BigDecimal,
    val spread: BigDecimal,
    val expiresAt: Instant,
    val status: QuoteStatus = QuoteStatus.ACTIVE,
    val createdAt: Instant = Instant.now()
)

enum class QuoteStatus(val value: String) {
    ACTIVE("active"),
    CONSUMED("consumed"),
    EXPIRED("expired");

    companion object {
        fun fromValue(value: String): QuoteStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown quote status: $value")
    }
}

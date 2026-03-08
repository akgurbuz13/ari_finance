package com.ari.platform.fx.api

import com.ari.platform.fx.internal.provider.FxRateProvider
import com.ari.platform.fx.internal.service.ConversionService
import com.ari.platform.fx.internal.service.QuoteService
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/fx")
class FxController(
    private val fxRateProvider: FxRateProvider,
    private val quoteService: QuoteService,
    private val conversionService: ConversionService
) {

    /**
     * Get current FX rates for a currency pair.
     * Returns the mid-market rate and inverse rate (no spread applied).
     */
    @GetMapping("/rates")
    fun getRates(
        @RequestParam source: String,
        @RequestParam target: String
    ): ResponseEntity<RateResponse> {
        val rate = fxRateProvider.getRate(source, target)
        return ResponseEntity.ok(
            RateResponse(
                sourceCurrency = rate.sourceCurrency,
                targetCurrency = rate.targetCurrency,
                rate = rate.rate.toPlainString(),
                inverseRate = rate.inverseRate.toPlainString(),
                fetchedAt = rate.fetchedAt.toString()
            )
        )
    }

    /**
     * Create a time-limited FX quote with spread applied.
     * The quote is valid for 30 seconds and can be consumed once via the /convert endpoint.
     */
    @PostMapping("/quotes")
    fun createQuote(
        @Valid @RequestBody request: CreateQuoteRequest
    ): ResponseEntity<QuoteResponse> {
        val quote = quoteService.createQuote(
            sourceCurrency = request.sourceCurrency,
            targetCurrency = request.targetCurrency,
            sourceAmount = request.sourceAmount
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            QuoteResponse(
                quoteId = quote.id.toString(),
                sourceCurrency = quote.sourceCurrency,
                targetCurrency = quote.targetCurrency,
                sourceAmount = quote.sourceAmount.toPlainString(),
                targetAmount = quote.targetAmount.toPlainString(),
                customerRate = quote.customerRate.toPlainString(),
                spread = quote.spread.toPlainString(),
                expiresAt = quote.expiresAt.toString(),
                status = quote.status.value
            )
        )
    }

    /**
     * Execute an FX conversion by consuming a valid quote.
     * Requires the user's source and target currency account IDs.
     */
    @PostMapping("/convert")
    fun executeConversion(
        @Valid @RequestBody request: ConvertRequest
    ): ResponseEntity<ConversionResponse> {
        val userId = UUID.fromString(
            SecurityContextHolder.getContext().authentication.principal as String
        )

        val result = conversionService.executeConversion(
            quoteId = UUID.fromString(request.quoteId),
            userId = userId,
            sourceAccountId = UUID.fromString(request.sourceAccountId),
            targetAccountId = UUID.fromString(request.targetAccountId)
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ConversionResponse(
                conversionId = result.conversionId.toString(),
                transactionId = result.transactionId.toString(),
                quoteId = result.quoteId.toString(),
                sourceCurrency = result.sourceCurrency,
                targetCurrency = result.targetCurrency,
                sourceAmount = result.sourceAmount.toPlainString(),
                targetAmount = result.targetAmount.toPlainString(),
                customerRate = result.customerRate.toPlainString(),
                executedAt = result.executedAt.toString()
            )
        )
    }
}

// ── Request DTOs ───────────────────────────────────────────────────────────────

data class CreateQuoteRequest(
    @field:NotBlank
    @field:Pattern(regexp = "TRY|EUR")
    val sourceCurrency: String,

    @field:NotBlank
    @field:Pattern(regexp = "TRY|EUR")
    val targetCurrency: String,

    @field:DecimalMin("0.01")
    val sourceAmount: BigDecimal
)

data class ConvertRequest(
    @field:NotBlank
    val quoteId: String,

    @field:NotBlank
    val sourceAccountId: String,

    @field:NotBlank
    val targetAccountId: String
)

// ── Response DTOs ──────────────────────────────────────────────────────────────

data class RateResponse(
    val sourceCurrency: String,
    val targetCurrency: String,
    val rate: String,
    val inverseRate: String,
    val fetchedAt: String,
    val timestamp: Instant = Instant.now()
)

data class QuoteResponse(
    val quoteId: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val sourceAmount: String,
    val targetAmount: String,
    val customerRate: String,
    val spread: String,
    val expiresAt: String,
    val status: String,
    val timestamp: Instant = Instant.now()
)

data class ConversionResponse(
    val conversionId: String,
    val transactionId: String,
    val quoteId: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val sourceAmount: String,
    val targetAmount: String,
    val customerRate: String,
    val executedAt: String,
    val timestamp: Instant = Instant.now()
)

package com.ari.platform.fx.event

import com.ari.platform.shared.event.DomainEvent
import java.math.BigDecimal
import java.util.UUID

/**
 * Published when a new FX quote is created.
 * Downstream consumers may use this for analytics, rate monitoring, or fraud detection.
 */
class QuoteCreated(
    val quoteId: UUID,
    val sourceCurrency: String,
    val targetCurrency: String,
    val sourceAmount: BigDecimal,
    val targetAmount: BigDecimal,
    val customerRate: BigDecimal
) : DomainEvent(
    aggregateType = "fx_quote",
    aggregateId = quoteId.toString(),
    eventType = "QuoteCreated"
)

/**
 * Published when an FX conversion has been successfully executed.
 * The quote has been consumed and ledger entries have been posted.
 */
class ConversionExecuted(
    val conversionId: UUID,
    val quoteId: UUID,
    val transactionId: UUID,
    val userId: UUID,
    val sourceCurrency: String,
    val targetCurrency: String,
    val sourceAmount: BigDecimal,
    val targetAmount: BigDecimal,
    val customerRate: BigDecimal
) : DomainEvent(
    aggregateType = "fx_conversion",
    aggregateId = conversionId.toString(),
    eventType = "ConversionExecuted"
)

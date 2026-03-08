package com.ari.platform.rails.event

import com.ari.platform.shared.event.DomainEvent
import java.math.BigDecimal
import java.util.UUID

/**
 * Published when a payment instruction has been successfully submitted to an external rail.
 * Downstream consumers can use this to track the payment lifecycle.
 */
class RailPaymentSubmitted(
    val paymentId: UUID,
    val provider: String,
    val externalReference: String,
    val amount: BigDecimal,
    val currency: String,
    val direction: String
) : DomainEvent(
    aggregateType = "rail_payment",
    aggregateId = paymentId.toString(),
    eventType = "RailPaymentSubmitted"
)

/**
 * Published when a payment has been confirmed (settled or rejected) by the external rail,
 * typically via a webhook callback.
 */
class RailPaymentConfirmed(
    val paymentId: UUID,
    val provider: String,
    val externalReference: String,
    val status: String
) : DomainEvent(
    aggregateType = "rail_payment",
    aggregateId = paymentId.toString(),
    eventType = "RailPaymentConfirmed"
)

package com.ova.platform.payments.event

import com.ova.platform.shared.event.DomainEvent
import java.math.BigDecimal
import java.util.UUID

class PaymentInitiated(
    val paymentOrderId: UUID,
    val paymentType: String,
    val senderAccountId: UUID,
    val receiverAccountId: UUID,
    val amount: BigDecimal,
    val currency: String
) : DomainEvent(
    aggregateType = "payment",
    aggregateId = paymentOrderId.toString(),
    eventType = "PaymentInitiated"
)

class PaymentCompleted(
    val paymentOrderId: UUID,
    val paymentType: String,
    val ledgerTransactionId: UUID,
    val amount: BigDecimal,
    val currency: String
) : DomainEvent(
    aggregateType = "payment",
    aggregateId = paymentOrderId.toString(),
    eventType = "PaymentCompleted"
)

class MintRequested(
    val paymentOrderId: UUID,
    val targetAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val region: String
) : DomainEvent(
    aggregateType = "payment",
    aggregateId = paymentOrderId.toString(),
    eventType = "MintRequested"
)

class BurnRequested(
    val paymentOrderId: UUID,
    val sourceAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val region: String
) : DomainEvent(
    aggregateType = "payment",
    aggregateId = paymentOrderId.toString(),
    eventType = "BurnRequested"
)

class CrossBorderBurnMintRequested(
    val paymentOrderId: UUID,
    val sourceAccountId: UUID,
    val targetAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val sourceChainId: Long,
    val targetChainId: Long
) : DomainEvent(
    aggregateType = "payment",
    aggregateId = paymentOrderId.toString(),
    eventType = "CrossBorderBurnMintRequested"
)

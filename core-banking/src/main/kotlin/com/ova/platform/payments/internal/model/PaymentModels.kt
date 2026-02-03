package com.ova.platform.payments.internal.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentOrder(
    val id: UUID = UUID.randomUUID(),
    val idempotencyKey: String,
    val type: PaymentType,
    val status: PaymentStatus = PaymentStatus.INITIATED,
    val senderAccountId: UUID,
    val receiverAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val feeAmount: BigDecimal = BigDecimal.ZERO,
    val feeCurrency: String? = null,
    val fxQuoteId: UUID? = null,
    val ledgerTransactionId: UUID? = null,
    val description: String? = null,
    val metadata: Map<String, Any>? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)

enum class PaymentType(val value: String) {
    DEPOSIT("deposit"),
    WITHDRAWAL("withdrawal"),
    DOMESTIC_P2P("domestic_p2p"),
    CROSS_BORDER("cross_border");

    companion object {
        fun fromValue(value: String): PaymentType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown payment type: $value")
    }
}

enum class PaymentStatus(val value: String) {
    INITIATED("initiated"),
    COMPLIANCE_CHECK("compliance_check"),
    PROCESSING("processing"),
    SETTLING("settling"),
    COMPLETED("completed"),
    FAILED("failed"),
    REVERSED("reversed");

    companion object {
        fun fromValue(value: String): PaymentStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown payment status: $value")
    }
}

data class PaymentStatusHistory(
    val id: Long? = null,
    val paymentOrderId: UUID,
    val fromStatus: PaymentStatus?,
    val toStatus: PaymentStatus,
    val reason: String? = null,
    val createdAt: Instant = Instant.now()
)

data class FxQuote(
    val id: UUID = UUID.randomUUID(),
    val sourceCurrency: String,
    val targetCurrency: String,
    val exchangeRate: BigDecimal,
    val sourceAmount: BigDecimal,
    val targetAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val feeCurrency: String,
    val expiresAt: Instant,
    val used: Boolean = false,
    val createdAt: Instant = Instant.now()
)

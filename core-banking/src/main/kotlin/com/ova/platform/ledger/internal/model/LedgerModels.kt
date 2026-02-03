package com.ova.platform.ledger.internal.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Transaction(
    val id: UUID = UUID.randomUUID(),
    val idempotencyKey: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val referenceId: String? = null,
    val metadata: Map<String, Any>? = null,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)

enum class TransactionType(val value: String) {
    DEPOSIT("deposit"),
    WITHDRAWAL("withdrawal"),
    P2P_TRANSFER("p2p_transfer"),
    FX_CONVERSION("fx_conversion"),
    CROSS_BORDER("cross_border"),
    MINT("mint"),
    BURN("burn"),
    FEE("fee");

    companion object {
        fun fromValue(value: String): TransactionType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown transaction type: $value")
    }
}

enum class TransactionStatus(val value: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    FAILED("failed"),
    REVERSED("reversed");

    companion object {
        fun fromValue(value: String): TransactionStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown transaction status: $value")
    }
}

data class Entry(
    val id: Long? = null,
    val transactionId: UUID,
    val accountId: UUID,
    val direction: EntryDirection,
    val amount: BigDecimal,
    val currency: String,
    val balanceAfter: BigDecimal,
    val createdAt: Instant = Instant.now()
)

enum class EntryDirection(val value: String) {
    DEBIT("debit"),
    CREDIT("credit");

    companion object {
        fun fromValue(value: String): EntryDirection =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown direction: $value")
    }
}

data class PostingInstruction(
    val accountId: UUID,
    val direction: EntryDirection,
    val amount: BigDecimal,
    val currency: String
)

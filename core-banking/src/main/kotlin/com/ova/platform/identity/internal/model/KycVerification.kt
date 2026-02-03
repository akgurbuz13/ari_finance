package com.ova.platform.identity.internal.model

import java.time.Instant
import java.util.UUID

data class KycVerification(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val provider: String,
    val providerRef: String,
    val status: KycStatus,
    val level: KycLevel,
    val decisionBy: UUID? = null,
    val decisionAt: Instant? = null,
    val rejectionReason: String? = null,
    val expiresAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

enum class KycStatus(val value: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
    EXPIRED("expired");

    companion object {
        fun fromValue(value: String): KycStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown KYC status: $value")
    }
}

enum class KycLevel(val value: String) {
    BASIC("basic"),
    ENHANCED("enhanced");

    companion object {
        fun fromValue(value: String): KycLevel =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown KYC level: $value")
    }
}

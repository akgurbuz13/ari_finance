package com.ova.platform.identity.internal.provider

import java.util.UUID

interface KycProviderAdapter {
    val providerName: String

    fun initiateVerification(userId: UUID, firstName: String?, lastName: String?, level: String): VerificationSession
    fun getVerificationStatus(providerRef: String): ProviderVerificationStatus
    fun parseWebhook(payload: String, signature: String?): WebhookEvent
}

data class VerificationSession(
    val providerRef: String,
    val sessionUrl: String?,
    val sessionToken: String?,
    val expiresInSeconds: Long = 1800
)

enum class ProviderVerificationStatus {
    PENDING, IN_PROGRESS, APPROVED, REJECTED, EXPIRED
}

data class WebhookEvent(
    val providerRef: String,
    val status: ProviderVerificationStatus,
    val rejectionReason: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

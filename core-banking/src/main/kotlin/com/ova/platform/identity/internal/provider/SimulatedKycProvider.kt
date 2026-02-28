package com.ova.platform.identity.internal.provider

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@ConditionalOnProperty(name = ["ari.kyc.provider"], havingValue = "simulated", matchIfMissing = true)
class SimulatedKycProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${ari.kyc.webhook.allow-unsigned:true}") private val allowUnsignedWebhooks: Boolean,
    @Value("\${ari.kyc.webhook.secret:}") private val webhookSecret: String
) : KycProviderAdapter {

    private val log = LoggerFactory.getLogger(SimulatedKycProvider::class.java)

    override val providerName = "simulated"

    override fun initiateVerification(
        userId: UUID,
        firstName: String?,
        lastName: String?,
        level: String
    ): VerificationSession {
        val providerRef = UUID.randomUUID().toString()
        val sessionToken = UUID.randomUUID().toString()

        log.info("Simulated KYC verification initiated for user {} with providerRef {}", userId, providerRef)

        return VerificationSession(
            providerRef = providerRef,
            sessionUrl = "https://verify.ova.dev/session/$providerRef",
            sessionToken = sessionToken,
            expiresInSeconds = 1800
        )
    }

    override fun getVerificationStatus(providerRef: String): ProviderVerificationStatus {
        log.info("Simulated KYC status check for providerRef {} - returning PENDING", providerRef)
        return ProviderVerificationStatus.PENDING
    }

    @Suppress("UNCHECKED_CAST")
    override fun parseWebhook(payload: String, signature: String?): WebhookEvent {
        if (!allowUnsignedWebhooks) {
            if (webhookSecret.isBlank()) {
                throw IllegalArgumentException("KYC webhook secret is not configured")
            }

            val provided = signature?.removePrefix("sha256=")?.lowercase()
                ?: throw IllegalArgumentException("Missing webhook signature")
            val expected = hmacSha256Hex(webhookSecret, payload)
            if (!constantTimeEquals(provided, expected)) {
                throw IllegalArgumentException("Invalid webhook signature")
            }
        }

        val data = objectMapper.readValue(payload, Map::class.java) as Map<String, Any>

        val providerRef = data["providerRef"] as? String
            ?: throw IllegalArgumentException("Missing providerRef in webhook payload")

        val statusStr = data["status"] as? String
            ?: throw IllegalArgumentException("Missing status in webhook payload")

        val status = when (statusStr.lowercase()) {
            "approved" -> ProviderVerificationStatus.APPROVED
            "rejected" -> ProviderVerificationStatus.REJECTED
            "pending" -> ProviderVerificationStatus.PENDING
            "in_progress" -> ProviderVerificationStatus.IN_PROGRESS
            "expired" -> ProviderVerificationStatus.EXPIRED
            else -> throw IllegalArgumentException("Unknown webhook status: $statusStr")
        }

        val reason = data["reason"] as? String

        val metadata = (data["metadata"] as? Map<String, String>) ?: emptyMap()

        log.info("Parsed simulated webhook: providerRef={}, status={}, reason={}", providerRef, status, reason)

        return WebhookEvent(
            providerRef = providerRef,
            status = status,
            rejectionReason = reason,
            metadata = metadata
        )
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}

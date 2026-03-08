package com.ari.platform.rails.api

import com.ari.platform.payments.internal.repository.RailReferenceRepository
import com.ari.platform.payments.internal.repository.WebhookEvent
import com.ari.platform.payments.internal.repository.WebhookEventRepository
import com.ari.platform.rails.internal.adapter.RailAdapter
import com.ari.platform.rails.internal.adapter.RailPaymentStatus
import com.ari.platform.rails.internal.adapter.RailWebhookResult
import com.ari.platform.rails.internal.service.RailService
import com.ari.platform.shared.exception.BadRequestException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/api/v1/webhooks/rails")
class RailWebhookController(
    private val adapters: List<RailAdapter>,
    private val railService: RailService,
    private val railReferenceRepository: RailReferenceRepository,
    private val webhookEventRepository: WebhookEventRepository,
    @Value("\${ari.rails.webhook.allow-unsigned:false}") private val allowUnsignedWebhooks: Boolean,
    @Value("\${ari.rails.webhook.fast-secret:}") private val fastWebhookSecret: String,
    @Value("\${ari.rails.webhook.eft-secret:}") private val eftWebhookSecret: String,
    @Value("\${ari.rails.webhook.sepa-secret:}") private val sepaWebhookSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val adaptersByProvider: Map<String, RailAdapter> by lazy {
        adapters.associateBy { it.providerId }
    }

    /**
     * Receives webhook callbacks from external payment rail providers.
     * Each provider sends status updates when a payment is processed, settled, or rejected.
     *
     * The endpoint validates the webhook signature (stub for now), delegates payload
     * parsing to the appropriate [RailAdapter], and publishes domain events via [RailService].
     */
    @PostMapping("/{provider}")
    fun handleWebhook(
        @PathVariable provider: String,
        @RequestBody payload: String,
        request: HttpServletRequest
    ): ResponseEntity<WebhookAckResponse> {
        log.info("Received webhook from provider={}, contentLength={}", provider, payload.length)

        val adapter = adaptersByProvider[provider]
            ?: throw BadRequestException("Unknown rail provider: $provider")

        // Extract headers for signature validation
        val headers = mutableMapOf<String, String>()
        request.headerNames?.toList()?.forEach { name ->
            request.getHeader(name)?.let { headers[name.lowercase()] = it }
        }

        // Validate webhook signature (stub implementation)
        if (!validateSignature(provider, payload, headers)) {
            log.warn("Invalid webhook signature from provider={}", provider)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(WebhookAckResponse(accepted = false, message = "Invalid signature"))
        }

        // Delegate payload parsing to the rail-specific adapter
        val result: RailWebhookResult = adapter.handleWebhook(payload, headers)

        // Webhook deduplication: check if this event has already been processed
        val eventId = extractEventId(provider, result, headers)
        val isNew = webhookEventRepository.tryInsert(
            WebhookEvent(
                provider = provider,
                eventId = eventId,
                eventType = result.status.name,
                payload = result.rawPayload
            )
        )

        if (!isNew) {
            log.info("Duplicate webhook detected provider={}, eventId={}", provider, eventId)
            return ResponseEntity.ok(
                WebhookAckResponse(
                    accepted = true,
                    message = "Duplicate webhook, already processed",
                    externalReference = result.externalReference,
                    status = result.status.name
                )
            )
        }

        log.info(
            "Webhook processed provider={}, externalRef={}, status={}",
            provider, result.externalReference, result.status
        )

        // Resolve internal payment ID from external reference via rail_references table
        val paymentId = resolvePaymentId(provider, result.externalReference)

        if (paymentId != null) {
            railService.processConfirmation(
                provider = provider,
                externalReference = result.externalReference,
                status = result.status,
                paymentId = paymentId
            )

            // Update rail reference status
            val railRef = railReferenceRepository.findByExternalReference(provider, result.externalReference)
            if (railRef != null) {
                val newStatus = when (result.status) {
                    RailPaymentStatus.COMPLETED -> "confirmed"
                    RailPaymentStatus.FAILED, RailPaymentStatus.REJECTED -> "failed"
                    else -> "submitted"
                }
                railReferenceRepository.updateStatus(railRef.id!!, newStatus, result.rawPayload)
            }

            webhookEventRepository.markProcessed(provider, eventId)
        } else {
            log.warn(
                "Could not resolve paymentId for externalRef={}, provider={}",
                result.externalReference, provider
            )
        }

        return ResponseEntity.ok(
            WebhookAckResponse(
                accepted = true,
                message = "Webhook processed",
                externalReference = result.externalReference,
                status = result.status.name
            )
        )
    }

    /**
     * Stub signature validation. In production this would verify HMAC-SHA256
     * signatures using provider-specific secrets.
     */
    private fun validateSignature(provider: String, payload: String, headers: Map<String, String>): Boolean {
        log.debug("Validating webhook signature for provider={}", provider)

        if (allowUnsignedWebhooks) {
            return true
        }

        val secret = when (provider) {
            "fast" -> fastWebhookSecret
            "eft" -> eftWebhookSecret
            "sepa", "sepa_instant" -> sepaWebhookSecret
            else -> ""
        }

        if (secret.isBlank()) {
            log.warn("Webhook secret is not configured for provider={}", provider)
            return false
        }

        val providedSignature = when (provider) {
            "fast" -> headers["x-fast-signature"] ?: headers["x-signature"]
            "eft" -> headers["x-eft-signature"] ?: headers["x-signature"]
            "sepa", "sepa_instant" -> headers["x-sepa-signature"] ?: headers["x-signature"]
            else -> headers["x-signature"]
        } ?: return false

        val expectedSignature = hmacSha256Hex(secret, payload)
        val normalizedProvided = providedSignature.removePrefix("sha256=").lowercase()
        return constantTimeEquals(normalizedProvided, expectedSignature)
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

    /**
     * Resolve the internal payment ID from an external rail reference
     * using the rail_references mapping table.
     */
    private fun resolvePaymentId(provider: String, externalReference: String): UUID? {
        val railRef = railReferenceRepository.findByExternalReference(provider, externalReference)
        return railRef?.paymentOrderId
    }

    /**
     * Extract a unique event ID for deduplication. Uses provider-specific header
     * if available, otherwise falls back to the external reference + status combo.
     */
    private fun extractEventId(provider: String, result: RailWebhookResult, headers: Map<String, String>): String {
        // Provider-specific event ID headers
        val headerEventId = when (provider) {
            "fast" -> headers["x-fast-event-id"]
            "eft" -> headers["x-eft-message-id"]
            "sepa" -> headers["x-sepa-event-id"]
            else -> null
        }
        return headerEventId ?: "${result.externalReference}:${result.status.name}"
    }
}

data class WebhookAckResponse(
    val accepted: Boolean,
    val message: String,
    val externalReference: String? = null,
    val status: String? = null,
    val timestamp: Instant = Instant.now()
)

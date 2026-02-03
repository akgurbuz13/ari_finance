package com.ova.platform.rails.api

import com.ova.platform.rails.internal.adapter.RailAdapter
import com.ova.platform.rails.internal.adapter.RailPaymentStatus
import com.ova.platform.rails.internal.adapter.RailWebhookResult
import com.ova.platform.rails.internal.service.RailService
import com.ova.platform.shared.exception.BadRequestException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/webhooks/rails")
class RailWebhookController(
    private val adapters: List<RailAdapter>,
    private val railService: RailService
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

        log.info(
            "Webhook processed provider={}, externalRef={}, status={}",
            provider, result.externalReference, result.status
        )

        // Process the status update through RailService
        // In a full implementation, the paymentId would be resolved from externalReference via a lookup table
        val paymentId = resolvePaymentId(result.externalReference)

        if (paymentId != null) {
            railService.processConfirmation(
                provider = provider,
                externalReference = result.externalReference,
                status = result.status,
                paymentId = paymentId
            )
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

        // Stub: always returns true. In production:
        // - FAST: verify TCMB certificate-based signature
        // - EFT: verify TCMB HMAC signature
        // - SEPA: verify bank gateway X-Signature header
        return true
    }

    /**
     * Resolve the internal payment ID from an external rail reference.
     * Stub: in production this would query a payments.rail_references mapping table.
     */
    private fun resolvePaymentId(externalReference: String): UUID? {
        // Stub: return null to indicate lookup not yet implemented
        return null
    }
}

data class WebhookAckResponse(
    val accepted: Boolean,
    val message: String,
    val externalReference: String? = null,
    val status: String? = null,
    val timestamp: Instant = Instant.now()
)

package com.ari.platform.rails.internal.adapter

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Defines the contract for interacting with external payment rail systems.
 * Each rail (FAST, EFT, SEPA) implements this interface with provider-specific logic.
 */
interface RailAdapter {

    /** Unique identifier for this rail provider (e.g. "fast", "eft", "sepa"). */
    val providerId: String

    /**
     * Submit a payment instruction to the external rail.
     * Returns a [RailSubmissionResult] containing the external reference and initial status.
     */
    fun submitPayment(request: RailPaymentRequest): RailSubmissionResult

    /**
     * Query the external rail for the current status of a previously submitted payment.
     */
    fun checkStatus(externalReference: String): RailPaymentStatus

    /**
     * Process an inbound webhook/callback payload from the rail provider.
     * Returns a [RailWebhookResult] describing the parsed status update.
     */
    fun handleWebhook(payload: String, headers: Map<String, String>): RailWebhookResult
}

// ── Shared DTOs ────────────────────────────────────────────────────────────────

data class RailPaymentRequest(
    val paymentId: UUID,
    val sourceIban: String,
    val destinationIban: String,
    val amount: BigDecimal,
    val currency: String,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class RailSubmissionResult(
    val externalReference: String,
    val status: RailPaymentStatus,
    val provider: String? = null,
    val submittedAt: Instant = Instant.now()
)

enum class RailPaymentStatus {
    SUBMITTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED
}

data class RailWebhookResult(
    val externalReference: String,
    val status: RailPaymentStatus,
    val rawPayload: String,
    val receivedAt: Instant = Instant.now()
)

// ── FAST Adapter (Turkey Instant Payment System) ───────────────────────────────

@Component
class FastAdapter : RailAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override val providerId: String = "fast"

    override fun submitPayment(request: RailPaymentRequest): RailSubmissionResult {
        log.info(
            "FAST: Submitting payment paymentId={}, dest={}, amount={} {}",
            request.paymentId, request.destinationIban, request.amount, request.currency
        )

        // Stub: in production this calls the TCMB FAST API
        val externalRef = "FAST-${UUID.randomUUID().toString().take(12).uppercase()}"

        log.info("FAST: Payment submitted externalRef={}", externalRef)

        return RailSubmissionResult(
            externalReference = externalRef,
            status = RailPaymentStatus.SUBMITTED
        )
    }

    override fun checkStatus(externalReference: String): RailPaymentStatus {
        log.info("FAST: Checking status for externalRef={}", externalReference)

        // Stub: always returns PROCESSING for now
        return RailPaymentStatus.PROCESSING
    }

    override fun handleWebhook(payload: String, headers: Map<String, String>): RailWebhookResult {
        log.info("FAST: Received webhook payload length={}", payload.length)

        // Stub: parse external reference and status from payload
        val externalRef = "FAST-STUB-WEBHOOK"

        return RailWebhookResult(
            externalReference = externalRef,
            status = RailPaymentStatus.COMPLETED,
            rawPayload = payload
        )
    }
}

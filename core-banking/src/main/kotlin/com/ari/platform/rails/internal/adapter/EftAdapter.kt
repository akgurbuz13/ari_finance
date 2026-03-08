package com.ari.platform.rails.internal.adapter

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Turkey EFT (Electronic Fund Transfer) adapter.
 *
 * EFT payments are batch-processed and typically settle within 1-2 hours during
 * banking hours (weekdays 08:30-17:30 TRT). This adapter communicates with the
 * Central Bank of Turkey (TCMB) EFT system.
 */
@Component
class EftAdapter : RailAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override val providerId: String = "eft"

    override fun submitPayment(request: RailPaymentRequest): RailSubmissionResult {
        log.info(
            "EFT: Submitting payment paymentId={}, dest={}, amount={} {}",
            request.paymentId, request.destinationIban, request.amount, request.currency
        )

        // Stub: in production this queues the payment for the next EFT batch window
        val externalRef = "EFT-${UUID.randomUUID().toString().take(12).uppercase()}"

        log.info("EFT: Payment queued for next batch window externalRef={}", externalRef)

        return RailSubmissionResult(
            externalReference = externalRef,
            status = RailPaymentStatus.SUBMITTED
        )
    }

    override fun checkStatus(externalReference: String): RailPaymentStatus {
        log.info("EFT: Checking status for externalRef={}", externalReference)

        // Stub: EFT payments are batch-processed, return PROCESSING
        return RailPaymentStatus.PROCESSING
    }

    override fun handleWebhook(payload: String, headers: Map<String, String>): RailWebhookResult {
        log.info("EFT: Received webhook payload length={}", payload.length)

        // Stub: parse settlement notification from TCMB
        val externalRef = "EFT-STUB-WEBHOOK"

        return RailWebhookResult(
            externalReference = externalRef,
            status = RailPaymentStatus.COMPLETED,
            rawPayload = payload
        )
    }
}

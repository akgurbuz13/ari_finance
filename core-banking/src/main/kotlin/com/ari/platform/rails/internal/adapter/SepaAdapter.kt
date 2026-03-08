package com.ari.platform.rails.internal.adapter

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * EU SEPA Credit Transfer adapter.
 *
 * SEPA Credit Transfers settle within one business day across the EU/EEA.
 * SEPA Instant Credit Transfers (SCT Inst) settle in under 10 seconds when
 * available. This adapter communicates with the bank's SEPA gateway.
 */
@Component
class SepaAdapter : RailAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override val providerId: String = "sepa"

    override fun submitPayment(request: RailPaymentRequest): RailSubmissionResult {
        log.info(
            "SEPA: Submitting credit transfer paymentId={}, dest={}, amount={} {}",
            request.paymentId, request.destinationIban, request.amount, request.currency
        )

        // Stub: in production this sends a pain.001 XML message to the SEPA gateway
        val externalRef = "SEPA-${UUID.randomUUID().toString().take(12).uppercase()}"

        log.info("SEPA: Credit transfer submitted externalRef={}", externalRef)

        return RailSubmissionResult(
            externalReference = externalRef,
            status = RailPaymentStatus.SUBMITTED
        )
    }

    override fun checkStatus(externalReference: String): RailPaymentStatus {
        log.info("SEPA: Checking status for externalRef={}", externalReference)

        // Stub: return PROCESSING for standard SEPA CT
        return RailPaymentStatus.PROCESSING
    }

    override fun handleWebhook(payload: String, headers: Map<String, String>): RailWebhookResult {
        log.info("SEPA: Received webhook payload length={}", payload.length)

        // Stub: parse pacs.002 status report
        val externalRef = "SEPA-STUB-WEBHOOK"

        return RailWebhookResult(
            externalReference = externalRef,
            status = RailPaymentStatus.COMPLETED,
            rawPayload = payload
        )
    }
}

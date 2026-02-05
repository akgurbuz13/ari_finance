package com.ova.platform.rails.internal.service

import com.ova.platform.rails.event.RailPaymentConfirmed
import com.ova.platform.rails.event.RailPaymentSubmitted
import com.ova.platform.rails.internal.adapter.RailAdapter
import com.ova.platform.rails.internal.adapter.RailPaymentRequest
import com.ova.platform.rails.internal.adapter.RailPaymentStatus
import com.ova.platform.rails.internal.adapter.RailSubmissionResult
import com.ova.platform.shared.config.RegionConfig
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.model.Region
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * Routes payment instructions to the correct rail adapter based on the configured region
 * and requested payment type.
 *
 * Turkey (TR) region: FAST (instant) and EFT (batch) rails are available.
 * EU region: SEPA Credit Transfer rail is available.
 */
@Service
class RailService(
    private val regionConfig: RegionConfig,
    private val adapters: List<RailAdapter>,
    private val outboxPublisher: OutboxPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val adaptersByProvider: Map<String, RailAdapter> by lazy {
        adapters.associateBy { it.providerId }
    }

    /**
     * Submit a deposit instruction via the appropriate rail.
     * For TR: defaults to FAST. For EU: defaults to SEPA.
     */
    fun submitDeposit(
        paymentId: UUID,
        sourceIban: String,
        destinationIban: String,
        amount: BigDecimal,
        currency: String,
        preferredRail: String? = null,
        description: String? = null
    ): RailSubmissionResult {
        val rail = resolveRail(preferredRail)

        log.info(
            "Submitting deposit via rail={}, paymentId={}, amount={} {}",
            rail.providerId, paymentId, amount, currency
        )

        val request = RailPaymentRequest(
            paymentId = paymentId,
            sourceIban = sourceIban,
            destinationIban = destinationIban,
            amount = amount,
            currency = currency,
            description = description
        )

        val result = rail.submitPayment(request).copy(provider = rail.providerId)

        outboxPublisher.publish(
            RailPaymentSubmitted(
                paymentId = paymentId,
                provider = rail.providerId,
                externalReference = result.externalReference,
                amount = amount,
                currency = currency,
                direction = "deposit"
            )
        )

        log.info(
            "Deposit submitted paymentId={}, rail={}, externalRef={}",
            paymentId, rail.providerId, result.externalReference
        )

        return result
    }

    /**
     * Submit a withdrawal instruction via the appropriate rail.
     * For TR: defaults to FAST. For EU: defaults to SEPA.
     */
    fun submitWithdrawal(
        paymentId: UUID,
        sourceIban: String,
        destinationIban: String,
        amount: BigDecimal,
        currency: String,
        preferredRail: String? = null,
        description: String? = null
    ): RailSubmissionResult {
        val rail = resolveRail(preferredRail)

        log.info(
            "Submitting withdrawal via rail={}, paymentId={}, amount={} {}",
            rail.providerId, paymentId, amount, currency
        )

        val request = RailPaymentRequest(
            paymentId = paymentId,
            sourceIban = sourceIban,
            destinationIban = destinationIban,
            amount = amount,
            currency = currency,
            description = description
        )

        val result = rail.submitPayment(request).copy(provider = rail.providerId)

        outboxPublisher.publish(
            RailPaymentSubmitted(
                paymentId = paymentId,
                provider = rail.providerId,
                externalReference = result.externalReference,
                amount = amount,
                currency = currency,
                direction = "withdrawal"
            )
        )

        log.info(
            "Withdrawal submitted paymentId={}, rail={}, externalRef={}",
            paymentId, rail.providerId, result.externalReference
        )

        return result
    }

    /**
     * Returns the list of available rail provider IDs for the current region.
     */
    fun getAvailableRails(): List<String> {
        return when (regionConfig.region) {
            Region.TR -> listOf("fast", "eft")
            Region.EU -> listOf("sepa")
        }
    }

    /**
     * Process a confirmed payment status update received from a webhook.
     * Publishes a [RailPaymentConfirmed] domain event.
     */
    fun processConfirmation(
        provider: String,
        externalReference: String,
        status: RailPaymentStatus,
        paymentId: UUID
    ) {
        log.info(
            "Processing rail confirmation provider={}, externalRef={}, status={}",
            provider, externalReference, status
        )

        outboxPublisher.publish(
            RailPaymentConfirmed(
                paymentId = paymentId,
                provider = provider,
                externalReference = externalReference,
                status = status.name
            )
        )
    }

    /**
     * Resolve the adapter to use. If a preferred rail is specified, validate it is available
     * in the current region. Otherwise, use the region default.
     */
    private fun resolveRail(preferredRail: String?): RailAdapter {
        val available = getAvailableRails()

        val railId = if (preferredRail != null) {
            if (preferredRail !in available) {
                throw BadRequestException(
                    "Rail '$preferredRail' is not available in region ${regionConfig.region.code}. " +
                        "Available rails: $available"
                )
            }
            preferredRail
        } else {
            // Default rail per region
            when (regionConfig.region) {
                Region.TR -> "fast"
                Region.EU -> "sepa"
            }
        }

        return adaptersByProvider[railId]
            ?: throw BadRequestException("Rail adapter not found for provider: $railId")
    }
}

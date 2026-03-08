package com.ari.platform.rails.internal.service

import com.ari.platform.payments.internal.repository.RailReferenceRepository
import com.ari.platform.rails.internal.adapter.RailAdapter
import com.ari.platform.rails.internal.adapter.RailPaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Polls external rail providers for status updates on pending/submitted payments.
 *
 * This acts as a fallback for cases where the webhook was missed or delayed.
 * Runs every 30 seconds and checks all rail references that are in 'pending' or 'submitted' status.
 */
@Service
class RailStatusPollerService(
    private val railReferenceRepository: RailReferenceRepository,
    private val adapters: List<RailAdapter>,
    private val railService: RailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val adaptersByProvider: Map<String, RailAdapter> by lazy {
        adapters.associateBy { it.providerId }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    fun pollPendingPayments() {
        val pendingRefs = railReferenceRepository.findPendingOrSubmitted(50)

        if (pendingRefs.isEmpty()) return

        log.debug("Polling {} pending/submitted rail references", pendingRefs.size)

        for (ref in pendingRefs) {
            try {
                val adapter = adaptersByProvider[ref.provider]
                if (adapter == null) {
                    log.warn("No adapter found for provider={}, skipping ref={}", ref.provider, ref.externalReference)
                    continue
                }

                val status = adapter.checkStatus(ref.externalReference)

                when (status) {
                    RailPaymentStatus.COMPLETED -> {
                        log.info(
                            "Poller: payment confirmed provider={}, ref={}, paymentId={}",
                            ref.provider, ref.externalReference, ref.paymentOrderId
                        )
                        railReferenceRepository.updateStatus(ref.id!!, "confirmed")
                        railService.processConfirmation(
                            provider = ref.provider,
                            externalReference = ref.externalReference,
                            status = status,
                            paymentId = ref.paymentOrderId
                        )
                    }
                    RailPaymentStatus.FAILED, RailPaymentStatus.REJECTED -> {
                        log.info(
                            "Poller: payment failed provider={}, ref={}, paymentId={}",
                            ref.provider, ref.externalReference, ref.paymentOrderId
                        )
                        railReferenceRepository.updateStatus(ref.id!!, "failed")
                        railService.processConfirmation(
                            provider = ref.provider,
                            externalReference = ref.externalReference,
                            status = status,
                            paymentId = ref.paymentOrderId
                        )
                    }
                    else -> {
                        log.debug(
                            "Poller: payment still processing provider={}, ref={}",
                            ref.provider, ref.externalReference
                        )
                    }
                }
            } catch (e: Exception) {
                log.error(
                    "Error polling status for ref={}, provider={}: {}",
                    ref.externalReference, ref.provider, e.message
                )
            }
        }
    }
}

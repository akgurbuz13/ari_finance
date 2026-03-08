package com.ari.platform.rails.internal.service

import com.ari.platform.payments.internal.model.PaymentType
import com.ari.platform.payments.internal.repository.PaymentOrderRepository
import com.ari.platform.payments.internal.service.DepositService
import com.ari.platform.payments.internal.service.WithdrawalService
import com.ari.platform.shared.event.OutboxEventRecord
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Listens for rail-related domain events dispatched by the OutboxPoller.
 *
 * When a rail confirms or rejects a payment (via webhook or poller), the
 * RailService publishes a RailPaymentConfirmed event. This listener routes
 * the confirmation to the appropriate payment service (deposit or withdrawal).
 */
@Component
class RailEventListener(
    private val depositService: DepositService,
    private val withdrawalService: WithdrawalService,
    private val paymentOrderRepository: PaymentOrderRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onOutboxEvent(event: OutboxEventRecord) {
        try {
            when (event.eventType) {
                "RailPaymentConfirmed" -> handleRailPaymentConfirmed(event)
                else -> { /* not our event */ }
            }
        } catch (e: Exception) {
            log.error(
                "Failed to process rail event type={}, id={}: {}",
                event.eventType, event.id, e.message, e
            )
        }
    }

    private fun handleRailPaymentConfirmed(event: OutboxEventRecord) {
        val payload = event.payload
        val paymentId = UUID.fromString(payload.get("paymentId").asText())
        val status = payload.get("status").asText()
        val provider = payload.get("provider").asText()
        val externalRef = payload.get("externalReference").asText()

        log.info(
            "Processing RailPaymentConfirmed paymentId={}, status={}, provider={}, ref={}",
            paymentId, status, provider, externalRef
        )

        val order = paymentOrderRepository.findById(paymentId)
        if (order == null) {
            log.warn("RailPaymentConfirmed: payment order not found id={}", paymentId)
            return
        }

        when (status) {
            "COMPLETED" -> {
                when (order.type) {
                    PaymentType.DEPOSIT -> depositService.completeDeposit(paymentId)
                    PaymentType.WITHDRAWAL -> withdrawalService.completeWithdrawal(paymentId)
                    else -> log.warn("Unexpected payment type {} for rail confirmation", order.type)
                }
            }
            "FAILED", "REJECTED" -> {
                when (order.type) {
                    PaymentType.WITHDRAWAL -> withdrawalService.reverseWithdrawal(
                        paymentId,
                        "Rail $status: $externalRef"
                    )
                    PaymentType.DEPOSIT -> {
                        // Deposits that fail at the rail level haven't been credited to the user yet,
                        // so no reversal needed — just mark the payment order as failed
                        log.info("Deposit rail failed paymentId={}, no reversal needed", paymentId)
                    }
                    else -> log.warn("Unexpected payment type {} for rail failure", order.type)
                }
            }
            else -> log.warn("Unexpected rail status {} for paymentId={}", status, paymentId)
        }
    }
}

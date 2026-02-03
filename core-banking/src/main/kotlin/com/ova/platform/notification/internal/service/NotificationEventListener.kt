package com.ova.platform.notification.internal.service

import com.fasterxml.jackson.databind.JsonNode
import com.ova.platform.identity.internal.repository.UserRepository
import com.ova.platform.ledger.internal.repository.AccountRepository
import com.ova.platform.payments.internal.repository.PaymentOrderRepository
import com.ova.platform.shared.event.OutboxEventRecord
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class NotificationEventListener(
    private val notificationService: NotificationService,
    private val emailService: EmailService,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onOutboxEvent(event: OutboxEventRecord) {
        try {
            when (event.eventType) {
                "PaymentCompleted" -> handlePaymentCompleted(event.payload)
                "UserCreated" -> handleUserCreated(event.payload)
                "KycApproved" -> handleKycApproved(event.payload)
                "KycRejected" -> handleKycRejected(event.payload)
                else -> log.debug("Ignoring event type: {}", event.eventType)
            }
        } catch (e: Exception) {
            log.error("Failed to process notification for event type={}, id={}: {}",
                event.eventType, event.id, e.message, e)
        }
    }

    private fun handlePaymentCompleted(payload: JsonNode) {
        val paymentOrderId = UUID.fromString(payload.get("paymentOrderId").asText())
        val amount = payload.get("amount").asText()
        val currency = payload.get("currency").asText()

        val paymentOrder = paymentOrderRepository.findById(paymentOrderId)
        if (paymentOrder == null) {
            log.warn("PaymentCompleted: payment order not found for id={}", paymentOrderId)
            return
        }

        val senderAccount = accountRepository.findById(paymentOrder.senderAccountId)
        val receiverAccount = accountRepository.findById(paymentOrder.receiverAccountId)

        if (senderAccount == null || receiverAccount == null) {
            log.warn("PaymentCompleted: could not resolve accounts for payment={}, sender={}, receiver={}",
                paymentOrderId, paymentOrder.senderAccountId, paymentOrder.receiverAccountId)
            return
        }

        val senderUser = userRepository.findById(senderAccount.userId)
        val receiverUser = userRepository.findById(receiverAccount.userId)

        val senderName = senderUser?.let { "${it.firstName ?: ""} ${it.lastName ?: ""}".trim() }
            ?.ifEmpty { null }
        val receiverName = receiverUser?.let { "${it.firstName ?: ""} ${it.lastName ?: ""}".trim() }
            ?.ifEmpty { null }

        try {
            notificationService.notifyPaymentSent(
                userId = senderAccount.userId,
                amount = amount,
                currency = currency,
                receiverName = receiverName
            )
            log.info("PaymentCompleted: sent notification to sender userId={} for payment={}",
                senderAccount.userId, paymentOrderId)
        } catch (e: Exception) {
            log.error("PaymentCompleted: failed to notify sender userId={} for payment={}: {}",
                senderAccount.userId, paymentOrderId, e.message, e)
        }

        try {
            notificationService.notifyPaymentReceived(
                userId = receiverAccount.userId,
                amount = amount,
                currency = currency,
                senderName = senderName
            )
            log.info("PaymentCompleted: sent notification to receiver userId={} for payment={}",
                receiverAccount.userId, paymentOrderId)
        } catch (e: Exception) {
            log.error("PaymentCompleted: failed to notify receiver userId={} for payment={}: {}",
                receiverAccount.userId, paymentOrderId, e.message, e)
        }
    }

    private fun handleUserCreated(payload: JsonNode) {
        val email = payload.get("email").asText()
        val userId = payload.get("userId").asText()

        try {
            emailService.send(
                to = email,
                subject = "Welcome to Ova",
                body = "Welcome to Ova! Your account has been created successfully. " +
                    "Complete your identity verification to unlock all features."
            )
            log.info("UserCreated: sent welcome email to userId={}", userId)
        } catch (e: Exception) {
            log.error("UserCreated: failed to send welcome email to userId={}: {}",
                userId, e.message, e)
        }
    }

    private fun handleKycApproved(payload: JsonNode) {
        val userId = UUID.fromString(payload.get("userId").asText())

        try {
            notificationService.notifyKycApproved(userId)
            log.info("KycApproved: sent notification to userId={}", userId)
        } catch (e: Exception) {
            log.error("KycApproved: failed to notify userId={}: {}", userId, e.message, e)
        }
    }

    private fun handleKycRejected(payload: JsonNode) {
        val userId = UUID.fromString(payload.get("userId").asText())
        val reason = payload.get("reason").asText()

        try {
            notificationService.notifyKycRejected(userId, reason)
            log.info("KycRejected: sent notification to userId={}", userId)
        } catch (e: Exception) {
            log.error("KycRejected: failed to notify userId={}: {}", userId, e.message, e)
        }
    }
}

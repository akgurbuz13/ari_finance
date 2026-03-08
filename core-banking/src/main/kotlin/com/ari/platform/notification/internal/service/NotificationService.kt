package com.ari.platform.notification.internal.service

import com.ari.platform.identity.internal.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationService(
    private val emailService: EmailService,
    private val smsService: SmsService,
    private val pushService: PushService,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun notifyPaymentReceived(userId: UUID, amount: String, currency: String, senderName: String?) {
        val user = userRepository.findById(userId) ?: return

        val message = "You received $amount $currency${senderName?.let { " from $it" } ?: ""}"

        emailService.send(user.email, "Payment Received", message)
        pushService.send(userId.toString(), "Payment Received", message)
    }

    fun notifyPaymentSent(userId: UUID, amount: String, currency: String, receiverName: String?) {
        val user = userRepository.findById(userId) ?: return

        val message = "You sent $amount $currency${receiverName?.let { " to $it" } ?: ""}"

        pushService.send(userId.toString(), "Payment Sent", message)
    }

    fun notifyKycApproved(userId: UUID) {
        val user = userRepository.findById(userId) ?: return

        emailService.send(user.email, "KYC Approved", "Your identity verification has been approved. You can now use all ARI features.")
        pushService.send(userId.toString(), "KYC Approved", "Your identity verification has been approved.")
    }

    fun notifyKycRejected(userId: UUID, reason: String) {
        val user = userRepository.findById(userId) ?: return

        emailService.send(user.email, "KYC Verification Update", "Your identity verification needs attention: $reason")
        pushService.send(userId.toString(), "KYC Update", "Your identity verification needs attention.")
    }

    fun notifyAccountFrozen(userId: UUID) {
        val user = userRepository.findById(userId) ?: return

        emailService.send(user.email, "Account Update", "Your account has been temporarily restricted. Please contact support.")
    }

    fun sendOtp(userId: UUID, otp: String) {
        val user = userRepository.findById(userId) ?: return

        smsService.send(user.phone, "Your ARI verification code is: $otp")
    }
}

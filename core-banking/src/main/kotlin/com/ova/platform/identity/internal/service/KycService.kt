package com.ova.platform.identity.internal.service

import com.ova.platform.identity.event.KycApproved
import com.ova.platform.identity.event.KycRejected
import com.ova.platform.identity.internal.model.KycLevel
import com.ova.platform.identity.internal.model.KycStatus
import com.ova.platform.identity.internal.model.KycVerification
import com.ova.platform.identity.internal.model.UserStatus
import com.ova.platform.identity.internal.provider.KycProviderAdapter
import com.ova.platform.identity.internal.provider.ProviderVerificationStatus
import com.ova.platform.identity.internal.repository.KycRepository
import com.ova.platform.identity.internal.repository.UserRepository
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.NotFoundException
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class KycService(
    private val kycRepository: KycRepository,
    private val userRepository: UserRepository,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService,
    private val providerAdapters: List<KycProviderAdapter>
) {

    private val log = LoggerFactory.getLogger(KycService::class.java)

    @Transactional
    fun initiateKyc(userId: UUID, provider: String, providerRef: String, level: KycLevel): KycVerification {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("User", userId.toString())

        val existing = kycRepository.findLatestByUserId(userId)
        if (existing?.status == KycStatus.PENDING) {
            throw BadRequestException("KYC verification already in progress")
        }

        val kyc = kycRepository.save(
            KycVerification(
                userId = userId,
                provider = provider,
                providerRef = providerRef,
                status = KycStatus.PENDING,
                level = level
            )
        )

        auditService.log(userId, "user", "initiate_kyc", "kyc", kyc.id.toString())
        return kyc
    }

    @Transactional
    fun initiateVerification(userId: UUID, provider: String, level: KycLevel): KycInitiationResponse {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("User", userId.toString())

        val existing = kycRepository.findLatestByUserId(userId)
        if (existing?.status == KycStatus.PENDING) {
            throw BadRequestException("KYC verification already in progress")
        }

        val adapter = providerAdapters.firstOrNull { it.providerName == provider }
            ?: throw BadRequestException("Unknown KYC provider: $provider")

        val session = adapter.initiateVerification(userId, user.firstName, user.lastName, level.value)

        val kyc = kycRepository.save(
            KycVerification(
                userId = userId,
                provider = provider,
                providerRef = session.providerRef,
                status = KycStatus.PENDING,
                level = level
            )
        )

        auditService.log(userId, "user", "initiate_kyc", "kyc", kyc.id.toString(),
            details = mapOf("provider" to provider, "level" to level.value))

        return KycInitiationResponse(
            verificationId = kyc.id,
            sessionUrl = session.sessionUrl,
            sessionToken = session.sessionToken,
            expiresInSeconds = session.expiresInSeconds
        )
    }

    @Transactional
    fun handleWebhookCallback(provider: String, payload: String, signature: String?) {
        val adapter = providerAdapters.firstOrNull { it.providerName == provider }
            ?: throw BadRequestException("Unknown KYC provider: $provider")

        val event = adapter.parseWebhook(payload, signature)

        val kyc = kycRepository.findByProviderRef(event.providerRef)
            ?: throw NotFoundException("KycVerification", "providerRef=${event.providerRef}")

        if (kyc.status != KycStatus.PENDING) {
            log.warn("KYC verification {} is already in status {}, ignoring webhook", kyc.id, kyc.status)
            return
        }

        when (event.status) {
            ProviderVerificationStatus.APPROVED -> {
                kycRepository.updateStatus(kyc.id, KycStatus.APPROVED, null, null)
                userRepository.updateStatus(kyc.userId, UserStatus.ACTIVE)
                outboxPublisher.publish(KycApproved(kyc.userId, kyc.id, kyc.level.value))
                auditService.log(null, "system", "kyc_webhook_approved", "kyc", kyc.id.toString(),
                    details = mapOf("provider" to provider))
            }
            ProviderVerificationStatus.REJECTED -> {
                val reason = event.rejectionReason ?: "Verification rejected by provider"
                kycRepository.updateStatus(kyc.id, KycStatus.REJECTED, null, reason)
                outboxPublisher.publish(KycRejected(kyc.userId, kyc.id, reason))
                auditService.log(null, "system", "kyc_webhook_rejected", "kyc", kyc.id.toString(),
                    details = mapOf("provider" to provider, "reason" to reason))
            }
            else -> {
                log.info("KYC webhook status {} for verification {} - no action", event.status, kyc.id)
            }
        }
    }

    @Transactional
    fun approveKyc(kycId: UUID, adminId: UUID) {
        val kyc = kycRepository.findById(kycId)
            ?: throw NotFoundException("KycVerification", kycId.toString())

        if (kyc.status != KycStatus.PENDING) {
            throw BadRequestException("KYC is not in pending status")
        }

        kycRepository.updateStatus(kycId, KycStatus.APPROVED, adminId, null)
        userRepository.updateStatus(kyc.userId, UserStatus.ACTIVE)

        outboxPublisher.publish(KycApproved(kyc.userId, kycId, kyc.level.value))
        auditService.log(adminId, "admin", "approve_kyc", "kyc", kycId.toString())
    }

    @Transactional
    fun rejectKyc(kycId: UUID, adminId: UUID, reason: String) {
        val kyc = kycRepository.findById(kycId)
            ?: throw NotFoundException("KycVerification", kycId.toString())

        if (kyc.status != KycStatus.PENDING) {
            throw BadRequestException("KYC is not in pending status")
        }

        kycRepository.updateStatus(kycId, KycStatus.REJECTED, adminId, reason)

        outboxPublisher.publish(KycRejected(kyc.userId, kycId, reason))
        auditService.log(adminId, "admin", "reject_kyc", "kyc", kycId.toString(),
            details = mapOf("reason" to reason))
    }

    fun getKycStatus(userId: UUID): KycVerification? {
        return kycRepository.findLatestByUserId(userId)
    }

    fun getKycHistory(userId: UUID): List<KycVerification> {
        return kycRepository.findByUserId(userId)
    }

    fun getKycById(kycId: UUID): KycVerification {
        return kycRepository.findById(kycId)
            ?: throw NotFoundException("KycVerification", kycId.toString())
    }
}

data class KycInitiationResponse(
    val verificationId: UUID,
    val sessionUrl: String?,
    val sessionToken: String?,
    val expiresInSeconds: Long
)

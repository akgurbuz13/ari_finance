package com.ova.platform.identity.internal.service

import com.ova.platform.identity.event.KycApproved
import com.ova.platform.identity.event.KycRejected
import com.ova.platform.identity.internal.model.KycLevel
import com.ova.platform.identity.internal.model.KycStatus
import com.ova.platform.identity.internal.model.KycVerification
import com.ova.platform.identity.internal.model.UserStatus
import com.ova.platform.identity.internal.repository.KycRepository
import com.ova.platform.identity.internal.repository.UserRepository
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.NotFoundException
import com.ova.platform.shared.security.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class KycService(
    private val kycRepository: KycRepository,
    private val userRepository: UserRepository,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService
) {

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
}

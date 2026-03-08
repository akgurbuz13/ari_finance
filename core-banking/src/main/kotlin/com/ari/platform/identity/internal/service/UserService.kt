package com.ari.platform.identity.internal.service

import com.ari.platform.identity.internal.model.User
import com.ari.platform.identity.internal.model.UserStatus
import com.ari.platform.identity.internal.repository.UserRepository
import com.ari.platform.shared.exception.NotFoundException
import com.ari.platform.shared.security.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val auditService: AuditService
) {

    fun getUser(userId: UUID): User {
        return userRepository.findById(userId)
            ?: throw NotFoundException("User", userId.toString())
    }

    @Transactional
    fun updateProfile(
        userId: UUID,
        firstName: String?,
        lastName: String?,
        dateOfBirth: java.time.LocalDate?,
        nationality: String?
    ): User {
        val user = getUser(userId)
        val updated = user.copy(
            firstName = firstName ?: user.firstName,
            lastName = lastName ?: user.lastName,
            dateOfBirth = dateOfBirth ?: user.dateOfBirth,
            nationality = nationality ?: user.nationality
        )
        userRepository.update(updated)
        auditService.log(userId, "user", "update_profile", "user", userId.toString())
        return updated
    }

    @Transactional
    fun suspendUser(userId: UUID, adminId: UUID) {
        userRepository.updateStatus(userId, UserStatus.SUSPENDED)
        auditService.log(adminId, "admin", "suspend_user", "user", userId.toString())
    }

    @Transactional
    fun reactivateUser(userId: UUID, adminId: UUID) {
        userRepository.updateStatus(userId, UserStatus.ACTIVE)
        auditService.log(adminId, "admin", "reactivate_user", "user", userId.toString())
    }
}

package com.ova.platform.identity.internal.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class User(
    val id: UUID = UUID.randomUUID(),
    val email: String,
    val phone: String,
    val passwordHash: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: LocalDate? = null,
    val nationality: String? = null,
    val status: UserStatus = UserStatus.PENDING_KYC,
    val region: String,
    val role: String = "USER",
    val totpSecret: String? = null,
    val totpEnabled: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class UserStatus(val value: String) {
    PENDING_KYC("pending_kyc"),
    ACTIVE("active"),
    SUSPENDED("suspended"),
    CLOSED("closed");

    companion object {
        fun fromValue(value: String): UserStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown user status: $value")
    }
}

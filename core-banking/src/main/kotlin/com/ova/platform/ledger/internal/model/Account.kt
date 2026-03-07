package com.ova.platform.ledger.internal.model

import java.time.Instant
import java.util.UUID

data class Account(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val currency: String,
    val accountType: AccountType,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val iban: String? = null,
    val region: String = "TR",
    val createdAt: Instant = Instant.now()
)

enum class AccountType(val value: String) {
    USER_WALLET("user_wallet"),
    SYSTEM_FLOAT("system_float"),
    FEE_REVENUE("fee_revenue"),
    SAFEGUARDING("safeguarding"),
    CROSS_BORDER_TRANSIT("cross_border_transit"),
    VEHICLE_ESCROW_HOLDING("vehicle_escrow_holding");

    companion object {
        fun fromValue(value: String): AccountType =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown account type: $value")
    }
}

enum class AccountStatus(val value: String) {
    ACTIVE("active"),
    FROZEN("frozen"),
    CLOSED("closed");

    companion object {
        fun fromValue(value: String): AccountStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown account status: $value")
    }
}

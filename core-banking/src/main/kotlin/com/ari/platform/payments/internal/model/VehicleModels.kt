package com.ari.platform.payments.internal.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class VehicleRegistration(
    val id: UUID = UUID.randomUUID(),
    val tokenId: Long? = null,
    val ownerUserId: UUID,
    val vin: String,
    val vinHash: String,
    val plateNumber: String,
    val plateHash: String,
    val make: String,
    val model: String,
    val year: Int,
    val color: String? = null,
    val mileage: Int? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val metadataUri: String? = null,
    val chainId: Long,
    val mintTxHash: String? = null,
    val status: VehicleStatus = VehicleStatus.PENDING,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class VehicleStatus(val value: String) {
    PENDING("PENDING"),
    MINTED("MINTED"),
    IN_ESCROW("IN_ESCROW"),
    TRANSFERRED("TRANSFERRED");

    companion object {
        fun fromValue(value: String): VehicleStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown vehicle status: $value")
    }
}

data class VehicleEscrow(
    val id: UUID = UUID.randomUUID(),
    val onChainEscrowId: Long? = null,
    val vehicleRegistrationId: UUID,
    val sellerUserId: UUID,
    val buyerUserId: UUID? = null,
    val saleAmount: BigDecimal,
    val feeAmount: BigDecimal = BigDecimal("50.00000000"),
    val currency: String = "TRY",
    val state: EscrowState = EscrowState.CREATED,
    val sellerConfirmed: Boolean = false,
    val buyerConfirmed: Boolean = false,
    val shareCode: String,
    val setupTxHash: String? = null,
    val fundTxHash: String? = null,
    val completeTxHash: String? = null,
    val cancelTxHash: String? = null,
    val paymentOrderId: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)

enum class EscrowState(val value: String) {
    CREATED("CREATED"),
    JOINING("JOINING"),
    SETUP_COMPLETE("SETUP_COMPLETE"),
    FUNDING("FUNDING"),
    FUNDED("FUNDED"),
    SELLER_CONFIRMED("SELLER_CONFIRMED"),
    BUYER_CONFIRMED("BUYER_CONFIRMED"),
    COMPLETING("COMPLETING"),
    COMPLETED("COMPLETED"),
    CANCELLING("CANCELLING"),
    CANCELLED("CANCELLED");

    companion object {
        fun fromValue(value: String): EscrowState =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown escrow state: $value")
    }
}

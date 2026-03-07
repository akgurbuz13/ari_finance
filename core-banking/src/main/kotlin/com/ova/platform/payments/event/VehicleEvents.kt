package com.ova.platform.payments.event

import com.ova.platform.shared.event.DomainEvent
import java.math.BigDecimal
import java.util.UUID

class VehicleMintRequested(
    val vehicleRegistrationId: UUID,
    val ownerUserId: UUID,
    val vinHash: String,
    val plateHash: String,
    val metadataUri: String
) : DomainEvent(
    aggregateType = "vehicle",
    aggregateId = vehicleRegistrationId.toString(),
    eventType = "VehicleMintRequested"
)

class EscrowSetupRequested(
    val escrowId: UUID,
    val vehicleTokenId: Long,
    val sellerWalletUserId: UUID,
    val buyerWalletUserId: UUID,
    val saleAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val currency: String
) : DomainEvent(
    aggregateType = "vehicle_escrow",
    aggregateId = escrowId.toString(),
    eventType = "EscrowSetupRequested"
)

class EscrowFundingRequested(
    val escrowId: UUID,
    val onChainEscrowId: Long,
    val totalAmount: BigDecimal,
    val currency: String
) : DomainEvent(
    aggregateType = "vehicle_escrow",
    aggregateId = escrowId.toString(),
    eventType = "EscrowFundingRequested"
)

class EscrowConfirmationRequested(
    val escrowId: UUID,
    val onChainEscrowId: Long,
    val role: String
) : DomainEvent(
    aggregateType = "vehicle_escrow",
    aggregateId = escrowId.toString(),
    eventType = "EscrowConfirmationRequested"
)

class EscrowCancellationRequested(
    val escrowId: UUID,
    val onChainEscrowId: Long?,
    val wasFunded: Boolean
) : DomainEvent(
    aggregateType = "vehicle_escrow",
    aggregateId = escrowId.toString(),
    eventType = "EscrowCancellationRequested"
)

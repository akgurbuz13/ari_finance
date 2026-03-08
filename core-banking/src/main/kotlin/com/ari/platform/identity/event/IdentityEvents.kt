package com.ari.platform.identity.event

import com.ari.platform.shared.event.DomainEvent
import java.util.UUID

class UserCreated(
    val userId: UUID,
    val email: String,
    val region: String
) : DomainEvent(
    aggregateType = "user",
    aggregateId = userId.toString(),
    eventType = "UserCreated"
)

class KycApproved(
    val userId: UUID,
    val kycId: UUID,
    val level: String
) : DomainEvent(
    aggregateType = "kyc",
    aggregateId = kycId.toString(),
    eventType = "KycApproved"
)

class KycRejected(
    val userId: UUID,
    val kycId: UUID,
    val reason: String
) : DomainEvent(
    aggregateType = "kyc",
    aggregateId = kycId.toString(),
    eventType = "KycRejected"
)

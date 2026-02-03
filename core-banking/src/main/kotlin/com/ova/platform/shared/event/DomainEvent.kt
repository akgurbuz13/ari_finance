package com.ova.platform.shared.event

import java.time.Instant
import java.util.UUID

abstract class DomainEvent(
    val eventId: UUID = UUID.randomUUID(),
    val occurredAt: Instant = Instant.now(),
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String
)

package com.ova.platform.notification.event

import com.ova.platform.shared.event.DomainEvent
import java.util.UUID

class NotificationSent(
    val userId: UUID,
    val channel: String,
    val type: String
) : DomainEvent(
    aggregateType = "notification",
    aggregateId = userId.toString(),
    eventType = "NotificationSent"
)

package com.ari.platform.ledger.event

import com.ari.platform.shared.event.DomainEvent
import java.util.UUID

class EntryPosted(
    val transactionId: UUID,
    val type: String,
    val entryCount: Int
) : DomainEvent(
    aggregateType = "ledger",
    aggregateId = transactionId.toString(),
    eventType = "EntryPosted"
)

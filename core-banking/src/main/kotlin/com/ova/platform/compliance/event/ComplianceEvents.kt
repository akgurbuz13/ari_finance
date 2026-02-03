package com.ova.platform.compliance.event

import com.ova.platform.shared.event.DomainEvent
import java.util.UUID

class ComplianceCaseCreated(
    val caseId: UUID,
    val userId: UUID,
    val type: String
) : DomainEvent(
    aggregateType = "compliance_case",
    aggregateId = caseId.toString(),
    eventType = "ComplianceCaseCreated"
)

class ComplianceCaseResolved(
    val caseId: UUID,
    val resolvedBy: UUID,
    val resolution: String
) : DomainEvent(
    aggregateType = "compliance_case",
    aggregateId = caseId.toString(),
    eventType = "ComplianceCaseResolved"
)

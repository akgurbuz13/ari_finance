package com.ari.platform.shared.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class OutboxPublisher(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    fun publish(event: DomainEvent) {
        jdbcTemplate.update(
            """
            INSERT INTO shared.outbox_events (aggregate_type, aggregate_id, event_type, payload)
            VALUES (?, ?, ?, ?::jsonb)
            """,
            event.aggregateType,
            event.aggregateId,
            event.eventType,
            objectMapper.writeValueAsString(event)
        )
    }

    fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}

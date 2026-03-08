package com.ari.platform.payments.internal.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class WebhookEvent(
    val id: Long? = null,
    val provider: String,
    val eventId: String,
    val eventType: String,
    val payload: String,
    val processed: Boolean = false,
    val processedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

@Repository
class WebhookEventRepository(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Attempt to insert a webhook event for deduplication.
     * Returns true if the event was inserted (first time), false if it already exists.
     */
    fun tryInsert(event: WebhookEvent): Boolean {
        return try {
            jdbcTemplate.update(
                """
                INSERT INTO payments.webhook_events (provider, event_id, event_type, payload)
                VALUES (?, ?, ?, ?::jsonb)
                ON CONFLICT (provider, event_id) DO NOTHING
                """,
                event.provider, event.eventId, event.eventType, event.payload
            ) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun markProcessed(provider: String, eventId: String) {
        jdbcTemplate.update(
            """
            UPDATE payments.webhook_events
            SET processed = true, processed_at = now()
            WHERE provider = ? AND event_id = ?
            """,
            provider, eventId
        )
    }

    fun isAlreadyProcessed(provider: String, eventId: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments.webhook_events WHERE provider = ? AND event_id = ? AND processed = true",
            Long::class.java,
            provider, eventId
        ) ?: 0L
        return count > 0
    }
}

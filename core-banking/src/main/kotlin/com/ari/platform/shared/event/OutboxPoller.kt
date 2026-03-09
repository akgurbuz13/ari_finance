package com.ari.platform.shared.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(name = ["ari.outbox.poller.enabled"], havingValue = "true", matchIfMissing = true)
class OutboxPoller(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun pollOutbox() {
        val events = jdbcTemplate.queryForList(
            """
            SELECT id, aggregate_type, aggregate_id, event_type, payload
            FROM shared.outbox_events
            WHERE NOT published
              AND event_type NOT IN ('MintRequested', 'BurnRequested', 'CrossChainTransferRequested',
                  'CrossBorderBurnMintRequested', 'VehicleMintRequested',
                  'EscrowSetupRequested', 'EscrowFundingRequested', 'EscrowConfirmationRequested', 'EscrowCancellationRequested')
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """
        )

        if (events.isEmpty()) return

        val ids = mutableListOf<Long>()
        for (event in events) {
            try {
                val outboxEvent = OutboxEventRecord(
                    id = event["id"] as Long,
                    aggregateType = event["aggregate_type"] as String,
                    aggregateId = event["aggregate_id"] as String,
                    eventType = event["event_type"] as String,
                    payload = objectMapper.readTree(event["payload"].toString())
                )
                applicationEventPublisher.publishEvent(outboxEvent)
                ids.add(outboxEvent.id)
            } catch (e: Exception) {
                log.error("Failed to process outbox event ${event["id"]}", e)
            }
        }

        if (ids.isNotEmpty()) {
            val placeholders = ids.joinToString(",") { "?" }
            jdbcTemplate.update(
                "UPDATE shared.outbox_events SET published = true WHERE id IN ($placeholders)",
                *ids.toTypedArray()
            )
        }
    }
}

data class OutboxEventRecord(
    val id: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: com.fasterxml.jackson.databind.JsonNode
)

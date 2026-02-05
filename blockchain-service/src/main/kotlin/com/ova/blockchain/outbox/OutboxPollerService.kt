package com.ova.blockchain.outbox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ova.blockchain.bridge.IcttBridgeService
import com.ova.blockchain.settlement.BurnService
import com.ova.blockchain.settlement.MintService
import com.ova.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.util.UUID

@Service
class OutboxPollerService(
    private val mintService: MintService,
    private val burnService: BurnService,
    private val bridgeService: IcttBridgeService,
    private val walletService: CustodialWalletService,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${ova.core-banking.url}") private val coreBankingUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    /**
     * Polls blockchain-relevant events from the shared outbox table.
     * Uses SELECT FOR UPDATE SKIP LOCKED to prevent competing consumers.
     */
    @Scheduled(fixedDelay = 2000)
    fun pollOutboxEvents() {
        try {
            val events = jdbcTemplate.query(
                """
                SELECT id, event_type, payload, aggregate_id
                FROM shared.outbox_events
                WHERE published = false
                  AND event_type IN ('MintRequested', 'BurnRequested', 'CrossChainTransferRequested')
                ORDER BY created_at ASC
                LIMIT 10
                FOR UPDATE SKIP LOCKED
                """,
                { rs, _ ->
                    OutboxEvent(
                        id = rs.getLong("id"),
                        eventType = rs.getString("event_type"),
                        payload = rs.getString("payload"),
                        aggregateId = rs.getString("aggregate_id")
                    )
                }
            )

            for (event in events) {
                try {
                    val payload = objectMapper.readTree(event.payload)
                    processEvent(event.eventType, payload)

                    // Mark as published
                    jdbcTemplate.update(
                        "UPDATE shared.outbox_events SET published = true, published_at = now() WHERE id = ?",
                        event.id
                    )
                } catch (e: Exception) {
                    log.error("Failed to process outbox event id={}, type={}: {}",
                        event.id, event.eventType, e.message, e)
                }
            }
        } catch (e: Exception) {
            log.error("Outbox polling error: {}", e.message)
        }
    }

    fun processEvent(eventType: String, payload: JsonNode) {
        log.info("Processing blockchain event: type={}", eventType)

        when (eventType) {
            "MintRequested" -> handleMintRequest(payload)
            "BurnRequested" -> handleBurnRequest(payload)
            "CrossChainTransferRequested" -> handleCrossChainTransfer(payload)
            else -> log.warn("Unknown event type: {}", eventType)
        }
    }

    private fun handleMintRequest(payload: JsonNode) {
        val currency = payload.get("currency").asText()
        val amount = BigDecimal(payload.get("amount").asText())
        val paymentOrderId = payload.get("paymentOrderId").asText()
        val targetAccountId = payload.get("targetAccountId").asText()

        // Resolve wallet address for the target account's owner
        val toAddress = resolveWalletAddress(targetAccountId, currency)

        val result = mintService.mint(toAddress, amount, currency, UUID.fromString(paymentOrderId))

        notifyCoreBanking(paymentOrderId, "mint", result.txHash, result.success)
    }

    private fun handleBurnRequest(payload: JsonNode) {
        val currency = payload.get("currency").asText()
        val amount = BigDecimal(payload.get("amount").asText())
        val paymentOrderId = payload.get("paymentOrderId").asText()
        val sourceAccountId = payload.get("sourceAccountId").asText()

        val fromAddress = resolveWalletAddress(sourceAccountId, currency)

        val result = burnService.burn(fromAddress, amount, currency, UUID.fromString(paymentOrderId))

        notifyCoreBanking(paymentOrderId, "burn", result.txHash, result.success)
    }

    private fun handleCrossChainTransfer(payload: JsonNode) {
        val fromChainId = payload.get("fromChainId").asLong()
        val toChainId = payload.get("toChainId").asLong()
        val fromAddress = payload.get("fromAddress").asText()
        val toAddress = payload.get("toAddress").asText()
        val amount = BigDecimal(payload.get("amount").asText())
        val currency = payload.get("currency").asText()

        val result = bridgeService.initiateBridgeTransfer(
            fromChainId, toChainId, fromAddress, toAddress, amount, currency
        )

        log.info("Cross-chain transfer result: status={}, sourceTx={}",
            result.status, result.sourceTxHash)
    }

    /**
     * Resolve a ledger account ID to a blockchain wallet address.
     * Queries core-banking to find the user who owns the account,
     * then gets/creates their custodial wallet.
     */
    private fun resolveWalletAddress(accountId: String, currency: String): String {
        // Look up the user ID for this ledger account from the shared DB
        val userId = jdbcTemplate.queryForObject(
            "SELECT owner_id FROM ledger.accounts WHERE id = ?",
            UUID::class.java,
            UUID.fromString(accountId)
        ) ?: throw RuntimeException("No owner found for account $accountId")

        val wallet = walletService.getOrCreateWalletForCurrency(userId, currency)
        return wallet.address
    }

    private fun notifyCoreBanking(paymentOrderId: String, operation: String, txHash: String, success: Boolean) {
        try {
            val callback = mapOf(
                "paymentOrderId" to paymentOrderId,
                "operation" to operation,
                "txHash" to txHash,
                "success" to success
            )
            restTemplate.postForEntity(
                "$coreBankingUrl/api/internal/settlement-confirmed",
                callback,
                Void::class.java
            )
            log.info("Core-banking notified: paymentOrderId={}, operation={}, success={}",
                paymentOrderId, operation, success)
        } catch (e: Exception) {
            log.error("Failed to notify core-banking for payment={}: {}", paymentOrderId, e.message)
        }
    }

    private data class OutboxEvent(
        val id: Long,
        val eventType: String,
        val payload: String,
        val aggregateId: String
    )
}

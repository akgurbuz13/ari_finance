package com.ova.blockchain.outbox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ova.blockchain.bridge.IcttBridgeService
import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.contract.ContractFactory
import com.ova.blockchain.settlement.BurnService
import com.ova.blockchain.settlement.MintService
import com.ova.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.util.UUID

@Service
class OutboxPollerService(
    private val mintService: MintService,
    private val burnService: BurnService,
    private val bridgeService: IcttBridgeService,
    private val walletService: CustodialWalletService,
    private val contractFactory: ContractFactory,
    private val blockchainConfig: BlockchainConfig,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${ari.core-banking.url}") private val coreBankingUrl: String,
    @Value("\${ari.core-banking.internal-api-key:}") private val internalApiKey: String
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
                  AND event_type IN ('MintRequested', 'BurnRequested', 'CrossChainTransferRequested', 'CrossBorderBurnMintRequested')
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
            "CrossBorderBurnMintRequested" -> handleCrossBorderBurnMint(payload)
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
     * Handle same-currency cross-border transfer via AriBurnMintBridge.
     * Burns on source chain, Teleporter delivers message, mints on dest chain.
     */
    private fun handleCrossBorderBurnMint(payload: JsonNode) {
        val currency = payload.get("currency").asText()
        val amount = BigDecimal(payload.get("amount").asText())
        val paymentOrderId = payload.get("paymentOrderId").asText()
        val sourceAccountId = payload.get("sourceAccountId").asText()
        val targetAccountId = payload.get("targetAccountId").asText()
        val sourceChainId = payload.get("sourceChainId").asLong()
        val targetChainId = payload.get("targetChainId").asLong()

        log.info("Processing CrossBorderBurnMint: paymentOrderId={}, {} {} from chain {} to chain {}",
            paymentOrderId, amount, currency, sourceChainId, targetChainId)

        try {
            // Resolve wallet addresses
            val sourceWalletAddress = resolveWalletAddress(sourceAccountId, currency)
            val targetWalletAddress = resolveWalletAddress(targetAccountId, currency)

            val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
            val bridgeOperatorCredentials = walletService.getBridgeOperatorCredentials()

            // Get the burn-mint bridge on the source chain
            val bridge = contractFactory.getBurnMintBridge(sourceChainId, bridgeOperatorCredentials)

            // Ensure source wallet is allowlisted on source chain stablecoin
            val sourceStablecoin = contractFactory.getStablecoin(sourceChainId, currency, bridgeOperatorCredentials)
            if (!sourceStablecoin.allowlisted(sourceWalletAddress)) {
                sourceStablecoin.addToAllowlist(sourceWalletAddress)
            }

            // Get the destination blockchain ID (bytes32 hex for Teleporter)
            val destBlockchainIdHex = blockchainConfig.getBlockchainId(targetChainId)
            val destBlockchainIdBytes = Numeric.hexStringToByteArray(destBlockchainIdHex)

            // Single bridge call: burns on source + sends Teleporter message
            // Teleporter relayers deliver the message to dest chain
            // Dest bridge receives message and mints tokens
            val receipt = bridge.burnAndBridge(destBlockchainIdBytes, targetWalletAddress, amountWei)

            val txHash = receipt.transactionHash
            log.info("CrossBorderBurnMint tx confirmed: txHash={}, paymentOrderId={}", txHash, paymentOrderId)

            notifyCoreBanking(paymentOrderId, "bridge_transfer", txHash, true)

        } catch (e: Exception) {
            log.error("CrossBorderBurnMint failed for paymentOrderId={}: {}", paymentOrderId, e.message, e)
            notifyCoreBanking(paymentOrderId, "bridge_transfer", "", false)
        }
    }

    /**
     * Resolve a ledger account ID to a blockchain wallet address.
     * Queries core-banking to find the user who owns the account,
     * then gets/creates their custodial wallet.
     */
    private fun resolveWalletAddress(accountId: String, currency: String): String {
        // Look up the user ID for this ledger account from the shared DB
        val userId = jdbcTemplate.queryForObject(
            "SELECT user_id FROM ledger.accounts WHERE id = ?",
            UUID::class.java,
            UUID.fromString(accountId)
        )

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

            if (internalApiKey.isBlank()) {
                throw IllegalStateException("Core banking internal API key is not configured")
            }

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Internal-Api-Key", internalApiKey)
            }

            restTemplate.postForEntity(
                "$coreBankingUrl/api/internal/settlement-confirmed",
                HttpEntity(callback, headers),
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

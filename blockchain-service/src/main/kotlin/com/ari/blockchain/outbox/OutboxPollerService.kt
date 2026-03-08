package com.ari.blockchain.outbox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ari.blockchain.bridge.IcttBridgeService
import com.ari.blockchain.config.BlockchainConfig
import com.ari.blockchain.contract.ContractFactory
import com.ari.blockchain.settlement.BurnService
import com.ari.blockchain.settlement.MintService
import com.ari.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.web3j.protocol.core.methods.response.TransactionReceipt
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
                  AND event_type IN ('MintRequested', 'BurnRequested', 'CrossChainTransferRequested', 'CrossBorderBurnMintRequested',
                      'VehicleMintRequested', 'EscrowSetupRequested', 'EscrowFundingRequested', 'EscrowConfirmationRequested', 'EscrowCancellationRequested')
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
            "VehicleMintRequested" -> handleVehicleMintRequest(payload)
            "EscrowSetupRequested" -> handleEscrowSetup(payload)
            "EscrowFundingRequested" -> handleEscrowFunding(payload)
            "EscrowConfirmationRequested" -> handleEscrowConfirmation(payload)
            "EscrowCancellationRequested" -> handleEscrowCancellation(payload)
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

    // ============ Vehicle Escrow Event Handlers ============

    private fun handleVehicleMintRequest(payload: JsonNode) {
        val vehicleRegistrationId = payload.get("vehicleRegistrationId").asText()
        val ownerUserId = payload.get("ownerUserId").asText()
        val vinHash = payload.get("vinHash").asText()
        val plateHash = payload.get("plateHash").asText()
        val metadataUri = payload.get("metadataUri").asText()

        log.info("Processing VehicleMintRequest: vehicleId={}", vehicleRegistrationId)

        try {
            val operatorCredentials = walletService.getBridgeOperatorCredentials()
            val nft = contractFactory.getVehicleNFT(operatorCredentials)

            val ownerWallet = walletService.getOrCreateWalletForCurrency(UUID.fromString(ownerUserId), "TRY")

            // Ensure owner is allowlisted on NFT contract
            if (!nft.kycAllowlisted(ownerWallet.address)) {
                nft.addToAllowlist(ownerWallet.address)
            }

            val vinHashBytes = org.web3j.utils.Numeric.hexStringToByteArray(vinHash)
            val plateHashBytes = org.web3j.utils.Numeric.hexStringToByteArray(plateHash)

            val receipt = nft.mint(ownerWallet.address, vinHashBytes, plateHashBytes, metadataUri)

            // Extract tokenId from receipt event logs (VehicleMinted event)
            val tokenId = extractIndexedId(receipt, VEHICLE_MINTED_TOPIC)

            notifyVehicleSettlement(mapOf(
                "type" to "VEHICLE_MINTED",
                "vehicleRegistrationId" to vehicleRegistrationId,
                "tokenId" to tokenId,
                "txHash" to receipt.transactionHash
            ))
        } catch (e: Exception) {
            log.error("VehicleMintRequest failed for vehicleId={}: {}", vehicleRegistrationId, e.message, e)
        }
    }

    private fun handleEscrowSetup(payload: JsonNode) {
        val escrowId = payload.get("escrowId").asText()
        val vehicleTokenId = payload.get("vehicleTokenId").asLong()
        val sellerUserId = payload.get("sellerWalletUserId").asText()
        val buyerUserId = payload.get("buyerWalletUserId").asText()
        val saleAmount = BigDecimal(payload.get("saleAmount").asText())
        val currency = payload.get("currency").asText()

        log.info("Processing EscrowSetupRequested: escrowId={}", escrowId)

        try {
            val operatorCredentials = walletService.getBridgeOperatorCredentials()
            val nft = contractFactory.getVehicleNFT(operatorCredentials)
            val escrowContract = contractFactory.getVehicleEscrow(operatorCredentials)

            val sellerWallet = walletService.getOrCreateWalletForCurrency(UUID.fromString(sellerUserId), currency)
            val buyerWallet = walletService.getOrCreateWalletForCurrency(UUID.fromString(buyerUserId), currency)

            // Ensure buyer is allowlisted on NFT
            if (!nft.kycAllowlisted(buyerWallet.address)) {
                nft.addToAllowlist(buyerWallet.address)
            }

            // Approve escrow contract for the NFT — must use seller's credentials (NFT owner)
            val sellerCredentials = walletService.getCredentials(UUID.fromString(sellerUserId))
            val sellerNft = contractFactory.getVehicleNFT(sellerCredentials)
            sellerNft.approve(blockchainConfig.vehicleEscrowAddress, java.math.BigInteger.valueOf(vehicleTokenId))

            // Create on-chain escrow
            val amountWei = saleAmount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
            val receipt = escrowContract.createEscrow(
                java.math.BigInteger.valueOf(vehicleTokenId),
                sellerWallet.address,
                buyerWallet.address,
                amountWei
            )

            // Extract onChainEscrowId from logs (EscrowCreated event)
            val onChainEscrowId = extractIndexedId(receipt, ESCROW_CREATED_TOPIC)

            notifyVehicleSettlement(mapOf(
                "type" to "ESCROW_SETUP_CONFIRMED",
                "escrowId" to escrowId,
                "onChainEscrowId" to onChainEscrowId,
                "txHash" to receipt.transactionHash
            ))
        } catch (e: Exception) {
            log.error("EscrowSetup failed for escrowId={}: {}", escrowId, e.message, e)
        }
    }

    private fun handleEscrowFunding(payload: JsonNode) {
        val escrowId = payload.get("escrowId").asText()
        val onChainEscrowId = payload.get("onChainEscrowId").asLong()
        val totalAmount = BigDecimal(payload.get("totalAmount").asText())
        val currency = payload.get("currency").asText()

        log.info("Processing EscrowFundingRequested: escrowId={}", escrowId)

        try {
            val operatorCredentials = walletService.getBridgeOperatorCredentials()
            val escrowContract = contractFactory.getVehicleEscrow(operatorCredentials)

            // Mint ariTRY to escrow contract address
            val amountWei = totalAmount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
            val stablecoin = contractFactory.getStablecoin(blockchainConfig.trL1ChainId, currency, operatorCredentials)
            stablecoin.mint(blockchainConfig.vehicleEscrowAddress, amountWei)

            // Mark escrow as funded
            val receipt = escrowContract.fundEscrow(java.math.BigInteger.valueOf(onChainEscrowId))

            notifyVehicleSettlement(mapOf(
                "type" to "ESCROW_FUNDED",
                "escrowId" to escrowId,
                "txHash" to receipt.transactionHash
            ))
        } catch (e: Exception) {
            log.error("EscrowFunding failed for escrowId={}: {}", escrowId, e.message, e)
        }
    }

    private fun handleEscrowConfirmation(payload: JsonNode) {
        val escrowId = payload.get("escrowId").asText()
        val onChainEscrowId = payload.get("onChainEscrowId").asLong()
        val role = payload.get("role").asText()

        log.info("Processing EscrowConfirmationRequested: escrowId={}, role={}", escrowId, role)

        try {
            val operatorCredentials = walletService.getBridgeOperatorCredentials()
            val escrowContract = contractFactory.getVehicleEscrow(operatorCredentials)

            val receipt = if (role == "SELLER") {
                escrowContract.sellerConfirm(java.math.BigInteger.valueOf(onChainEscrowId))
            } else {
                escrowContract.buyerConfirm(java.math.BigInteger.valueOf(onChainEscrowId))
            }

            // Check if EscrowCompleted event was emitted (both confirmed)
            val completed = receipt.logs.any { log ->
                log.topics.isNotEmpty() && log.topics[0] == ESCROW_COMPLETED_TOPIC
            }

            notifyVehicleSettlement(mapOf(
                "type" to "ESCROW_CONFIRMED",
                "escrowId" to escrowId,
                "role" to role,
                "completed" to completed,
                "txHash" to receipt.transactionHash
            ))
        } catch (e: Exception) {
            log.error("EscrowConfirmation failed for escrowId={}: {}", escrowId, e.message, e)
        }
    }

    private fun handleEscrowCancellation(payload: JsonNode) {
        val escrowId = payload.get("escrowId").asText()
        val onChainEscrowId = payload.get("onChainEscrowId")?.asLong()

        log.info("Processing EscrowCancellationRequested: escrowId={}", escrowId)

        try {
            if (onChainEscrowId != null && onChainEscrowId > 0) {
                val operatorCredentials = walletService.getBridgeOperatorCredentials()
                val escrowContract = contractFactory.getVehicleEscrow(operatorCredentials)
                escrowContract.cancel(java.math.BigInteger.valueOf(onChainEscrowId))
            }

            notifyVehicleSettlement(mapOf(
                "type" to "ESCROW_CANCELLED",
                "escrowId" to escrowId,
                "txHash" to ""
            ))
        } catch (e: Exception) {
            log.error("EscrowCancellation failed for escrowId={}: {}", escrowId, e.message, e)
        }
    }

    private val VEHICLE_MINTED_TOPIC = org.web3j.crypto.Hash.sha3String(
        "VehicleMinted(uint256,address,bytes32,bytes32)"
    )
    private val ESCROW_CREATED_TOPIC = org.web3j.crypto.Hash.sha3String(
        "EscrowCreated(uint256,uint256,address,address,uint256,uint256)"
    )
    private val ESCROW_COMPLETED_TOPIC = org.web3j.crypto.Hash.sha3String(
        "EscrowCompleted(uint256,address,address,uint256,uint256)"
    )

    /**
     * Extract an indexed uint256 (topic[1]) from a specific event in a transaction receipt.
     * Filters by event topic to avoid matching the wrong event.
     */
    private fun extractIndexedId(receipt: TransactionReceipt, eventTopic: String? = null): Long {
        for (log in receipt.logs) {
            if (log.topics.size >= 2) {
                if (eventTopic != null && log.topics[0] != eventTopic) continue
                try {
                    return java.math.BigInteger(log.topics[1].removePrefix("0x"), 16).toLong()
                } catch (_: Exception) { }
            }
        }
        return -1
    }

    private fun notifyVehicleSettlement(data: Map<String, Any>) {
        try {
            postToCoreBanking("/api/internal/vehicle-settlement", data)
            log.info("Vehicle settlement notified: type={}", data["type"])
        } catch (e: Exception) {
            log.error("Failed to notify vehicle settlement: {}", e.message)
        }
    }

    /**
     * Resolve a ledger account ID to a blockchain wallet address.
     * Queries core-banking to find the user who owns the account,
     * then gets/creates their custodial wallet.
     */
    private fun resolveWalletAddress(accountId: String, currency: String): String {
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
            postToCoreBanking("/api/internal/settlement-confirmed", mapOf(
                "paymentOrderId" to paymentOrderId,
                "operation" to operation,
                "txHash" to txHash,
                "success" to success
            ))
            log.info("Core-banking notified: paymentOrderId={}, operation={}, success={}",
                paymentOrderId, operation, success)
        } catch (e: Exception) {
            log.error("Failed to notify core-banking for payment={}: {}", paymentOrderId, e.message)
        }
    }

    private fun postToCoreBanking(path: String, data: Map<String, Any>) {
        require(internalApiKey.isNotBlank()) { "Core banking internal API key is not configured" }

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Internal-Api-Key", internalApiKey)
        }

        restTemplate.postForEntity(
            "$coreBankingUrl$path",
            HttpEntity(data, headers),
            Void::class.java
        )
    }

    private data class OutboxEvent(
        val id: Long,
        val eventType: String,
        val payload: String,
        val aggregateId: String
    )
}

package com.ova.blockchain.outbox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ova.blockchain.bridge.IcttBridgeService
import com.ova.blockchain.settlement.BurnService
import com.ova.blockchain.settlement.MintService
import com.ova.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${ova.core-banking.url}") private val coreBankingUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    @Scheduled(fixedDelay = 2000)
    fun pollOutboxEvents() {
        // TODO: Read outbox events from shared DB or via REST API from core-banking
        // For now, this service processes events via Spring application events or REST callbacks
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
        val toAddress = payload.get("toAddress").asText()
        val paymentOrderId = payload.get("paymentOrderId").asText()

        val result = mintService.mint(toAddress, amount, currency)

        // Callback to core-banking
        notifyCoreBanking(paymentOrderId, "mint", result.txHash, result.success)
    }

    private fun handleBurnRequest(payload: JsonNode) {
        val currency = payload.get("currency").asText()
        val amount = BigDecimal(payload.get("amount").asText())
        val fromAddress = payload.get("fromAddress").asText()
        val paymentOrderId = payload.get("paymentOrderId").asText()

        val result = burnService.burn(fromAddress, amount, currency)

        notifyCoreBanking(paymentOrderId, "burn", result.txHash, result.success)
    }

    private fun handleCrossChainTransfer(payload: JsonNode) {
        val fromChainId = payload.get("fromChainId").asLong()
        val toChainId = payload.get("toChainId").asLong()
        val fromAddress = payload.get("fromAddress").asText()
        val toAddress = payload.get("toAddress").asText()
        val amount = BigDecimal(payload.get("amount").asText())
        val currency = payload.get("currency").asText()

        val result = bridgeService.initiateCrossChainTransfer(
            fromChainId, toChainId, fromAddress, toAddress, amount, currency
        )

        log.info("Cross-chain transfer result: status={}, sourceTx={}",
            result.status, result.sourceTxHash)
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
            log.error("Failed to notify core-banking for payment={}", paymentOrderId, e)
        }
    }
}

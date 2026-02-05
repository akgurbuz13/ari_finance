package com.ova.blockchain.bridge

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.contract.ContractFactory
import com.ova.blockchain.repository.BlockchainTransaction
import com.ova.blockchain.repository.BlockchainTransactionRepository
import com.ova.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

/**
 * Service for handling cross-chain token transfers via Avalanche ICTT bridge.
 *
 * ICTT ARCHITECTURE:
 * - TokenHome: Deployed where native token exists (locks/unlocks tokens)
 * - TokenRemote: Deployed on remote chains (mints/burns wrapped tokens)
 * - Teleporter: Handles cross-chain message delivery with BLS signatures
 *
 * For Ova's dual-L1 setup:
 * - TR L1: TokenHome for ovaTRY, TokenRemote for wrapped ovaEUR (wEUR)
 * - EU L1: TokenHome for ovaEUR, TokenRemote for wrapped ovaTRY (wTRY)
 *
 * Cross-border flow (TRY -> EUR):
 * 1. User initiates on TR L1 -> TokenHome locks TRY
 * 2. Teleporter delivers message to EU L1
 * 3. TokenRemote on EU L1 mints wTRY
 * 4. FX conversion (off-chain or on-chain)
 * 5. EUR delivered to user
 */
@Service
class IcttBridgeService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val contractFactory: ContractFactory,
    private val walletService: CustodialWalletService,
    private val txRepository: BlockchainTransactionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Chain identifiers (Avalanche blockchain IDs as bytes32)
        val TR_L1_CHAIN_ID: Long = 99999
        val EU_L1_CHAIN_ID: Long = 99998

        // Fee settings
        val DEFAULT_BRIDGE_FEE = BigDecimal("0.001") // 0.1% bridge fee
        val MIN_BRIDGE_AMOUNT = BigDecimal("1.0")
        val MAX_BRIDGE_AMOUNT = BigDecimal("1000000.0")

        // Teleporter gas settings
        const val TELEPORTER_GAS_LIMIT = 300_000L
    }

    data class BridgeTransferResult(
        val transferId: String,
        val sourceTxHash: String,
        val destinationTxHash: String?,
        val status: BridgeStatus,
        val sourceChainId: Long,
        val destinationChainId: Long,
        val amount: BigDecimal,
        val feeAmount: BigDecimal,
        val error: String? = null
    )

    enum class BridgeStatus {
        INITIATED,          // Lock/burn initiated on source chain
        PENDING_RELAY,      // Waiting for Teleporter relay
        RELAYED,            // Message relayed, pending finalization
        COMPLETED,          // Mint/unlock completed on destination
        FAILED              // Transfer failed
    }

    data class BridgeQuote(
        val sourceChainId: Long,
        val destinationChainId: Long,
        val inputAmount: BigDecimal,
        val outputAmount: BigDecimal,
        val bridgeFee: BigDecimal,
        val relayerFee: BigDecimal,
        val estimatedTimeSeconds: Int
    )

    /**
     * Get a quote for a cross-chain transfer.
     */
    fun getBridgeQuote(
        sourceChainId: Long,
        destinationChainId: Long,
        amount: BigDecimal,
        currency: String
    ): BridgeQuote {
        require(amount >= MIN_BRIDGE_AMOUNT) { "Amount below minimum ($MIN_BRIDGE_AMOUNT)" }
        require(amount <= MAX_BRIDGE_AMOUNT) { "Amount above maximum ($MAX_BRIDGE_AMOUNT)" }

        val bridgeFee = amount.multiply(DEFAULT_BRIDGE_FEE)
        val relayerFee = BigDecimal("0.01") // Fixed relayer fee
        val outputAmount = amount.subtract(bridgeFee).subtract(relayerFee)

        // Estimate time based on chain finality + relay
        val estimatedTime = when {
            sourceChainId == TR_L1_CHAIN_ID && destinationChainId == EU_L1_CHAIN_ID -> 120 // 2 minutes
            sourceChainId == EU_L1_CHAIN_ID && destinationChainId == TR_L1_CHAIN_ID -> 120
            else -> 300 // 5 minutes for unknown routes
        }

        return BridgeQuote(
            sourceChainId = sourceChainId,
            destinationChainId = destinationChainId,
            inputAmount = amount,
            outputAmount = outputAmount,
            bridgeFee = bridgeFee,
            relayerFee = relayerFee,
            estimatedTimeSeconds = estimatedTime
        )
    }

    /**
     * Initiate a cross-chain transfer by locking native tokens via TokenHome.
     */
    fun initiateBridgeTransfer(
        sourceChainId: Long,
        destinationChainId: Long,
        fromAddress: String,
        toAddress: String,
        amount: BigDecimal,
        currency: String
    ): BridgeTransferResult {
        log.info(
            "Initiating ICTT bridge transfer: {} {} from chain {} to chain {}",
            amount, currency, sourceChainId, destinationChainId
        )

        val quote = getBridgeQuote(sourceChainId, destinationChainId, amount, currency)
        val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
        val feeAmountWei = quote.relayerFee.multiply(BigDecimal.TEN.pow(18)).toBigInteger()

        val transferId = generateTransferId(sourceChainId, fromAddress, toAddress, amount)
        val credentials = walletService.getBridgeOperatorCredentials()

        try {
            // Get the bridge adapter for the source chain
            val bridgeAdapter = contractFactory.getBridgeAdapter(sourceChainId, credentials)

            // Convert destination chain ID to bytes32
            val destChainIdBytes32 = chainIdToBytes32(destinationChainId)

            // Call bridgeNativeTokens on the adapter
            // This will:
            // 1. Transfer tokens from user to adapter
            // 2. Approve and call TokenHome.bridgeTokens
            // 3. Lock tokens and emit Teleporter message
            val receipt = bridgeAdapter.bridgeNativeTokens(
                toAddress,
                amountWei,
                feeAmountWei
            )

            val sourceTxHash = receipt.transactionHash

            // Record the transaction
            txRepository.save(
                BlockchainTransaction(
                    txHash = sourceTxHash,
                    chainId = sourceChainId,
                    operation = "bridge_initiate",
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "initiated",
                    blockNumber = receipt.blockNumber.toLong(),
                    gasUsed = receipt.gasUsed.toLong(),
                    confirmedAt = Instant.now(),
                    metadata = mapOf(
                        "transferId" to transferId,
                        "destinationChainId" to destinationChainId.toString(),
                        "bridgeFee" to quote.bridgeFee.toPlainString(),
                        "relayerFee" to quote.relayerFee.toPlainString()
                    ).toString()
                )
            )

            log.info(
                "Bridge transfer initiated: transferId={}, sourceTx={}, amount={}",
                transferId, sourceTxHash, amount
            )

            return BridgeTransferResult(
                transferId = transferId,
                sourceTxHash = sourceTxHash,
                destinationTxHash = null,
                status = BridgeStatus.INITIATED,
                sourceChainId = sourceChainId,
                destinationChainId = destinationChainId,
                amount = amount,
                feeAmount = quote.bridgeFee.add(quote.relayerFee)
            )

        } catch (e: Exception) {
            log.error("Bridge transfer initiation failed: {}", e.message, e)

            // Record failed attempt
            txRepository.save(
                BlockchainTransaction(
                    txHash = "failed-bridge-$transferId",
                    chainId = sourceChainId,
                    operation = "bridge_initiate",
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "failed",
                    errorMessage = e.message
                )
            )

            return BridgeTransferResult(
                transferId = transferId,
                sourceTxHash = "",
                destinationTxHash = null,
                status = BridgeStatus.FAILED,
                sourceChainId = sourceChainId,
                destinationChainId = destinationChainId,
                amount = amount,
                feeAmount = BigDecimal.ZERO,
                error = e.message
            )
        }
    }

    /**
     * Bridge wrapped tokens back to their home chain via TokenRemote.
     */
    fun bridgeWrappedTokensBack(
        sourceChainId: Long,
        destinationChainId: Long,
        fromAddress: String,
        toAddress: String,
        amount: BigDecimal,
        currency: String
    ): BridgeTransferResult {
        log.info(
            "Bridging wrapped tokens back: {} {} from chain {} to home chain {}",
            amount, currency, sourceChainId, destinationChainId
        )

        val quote = getBridgeQuote(sourceChainId, destinationChainId, amount, currency)
        val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
        val feeAmountWei = quote.relayerFee.multiply(BigDecimal.TEN.pow(18)).toBigInteger()

        val transferId = generateTransferId(sourceChainId, fromAddress, toAddress, amount)
        val credentials = walletService.getBridgeOperatorCredentials()

        try {
            val bridgeAdapter = contractFactory.getBridgeAdapter(sourceChainId, credentials)

            // Call bridgeWrappedTokensBack on the adapter
            // This will:
            // 1. Transfer wrapped tokens from user
            // 2. Burn via TokenRemote
            // 3. Send Teleporter message to unlock on home chain
            val receipt = bridgeAdapter.bridgeWrappedTokensBack(
                toAddress,
                amountWei,
                feeAmountWei
            )

            val sourceTxHash = receipt.transactionHash

            txRepository.save(
                BlockchainTransaction(
                    txHash = sourceTxHash,
                    chainId = sourceChainId,
                    operation = "bridge_back",
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "initiated",
                    blockNumber = receipt.blockNumber.toLong(),
                    gasUsed = receipt.gasUsed.toLong(),
                    confirmedAt = Instant.now()
                )
            )

            log.info("Bridge back initiated: transferId={}, sourceTx={}", transferId, sourceTxHash)

            return BridgeTransferResult(
                transferId = transferId,
                sourceTxHash = sourceTxHash,
                destinationTxHash = null,
                status = BridgeStatus.INITIATED,
                sourceChainId = sourceChainId,
                destinationChainId = destinationChainId,
                amount = amount,
                feeAmount = quote.bridgeFee.add(quote.relayerFee)
            )

        } catch (e: Exception) {
            log.error("Bridge back failed: {}", e.message, e)

            return BridgeTransferResult(
                transferId = transferId,
                sourceTxHash = "",
                destinationTxHash = null,
                status = BridgeStatus.FAILED,
                sourceChainId = sourceChainId,
                destinationChainId = destinationChainId,
                amount = amount,
                feeAmount = BigDecimal.ZERO,
                error = e.message
            )
        }
    }

    /**
     * Check the status of a bridge transfer.
     */
    fun getBridgeTransferStatus(transferId: String): BridgeTransferResult? {
        // Look up by transfer ID in transaction metadata
        val txs = txRepository.findByTransferId(transferId)

        if (txs.isEmpty()) {
            return null
        }

        val initTx = txs.firstOrNull { it.operation == "bridge_initiate" || it.operation == "bridge_back" }
        val completeTx = txs.firstOrNull { it.operation == "bridge_complete" }

        val status = when {
            completeTx?.status == "confirmed" -> BridgeStatus.COMPLETED
            initTx?.status == "failed" -> BridgeStatus.FAILED
            initTx?.status == "initiated" -> BridgeStatus.PENDING_RELAY
            else -> BridgeStatus.INITIATED
        }

        return BridgeTransferResult(
            transferId = transferId,
            sourceTxHash = initTx?.txHash ?: "",
            destinationTxHash = completeTx?.txHash,
            status = status,
            sourceChainId = initTx?.chainId ?: 0,
            destinationChainId = completeTx?.chainId ?: 0,
            amount = initTx?.amount ?: BigDecimal.ZERO,
            feeAmount = BigDecimal.ZERO, // Fee already deducted
            error = initTx?.errorMessage
        )
    }

    /**
     * Mark a bridge transfer as completed (called when destination chain confirms).
     * This is typically called by the event listener when TokenRemote mints
     * or TokenHome releases tokens.
     */
    fun markBridgeTransferCompleted(
        transferId: String,
        destinationChainId: Long,
        destinationTxHash: String,
        recipient: String,
        amount: BigDecimal
    ) {
        log.info(
            "Marking bridge transfer completed: transferId={}, destTx={}",
            transferId, destinationTxHash
        )

        txRepository.save(
            BlockchainTransaction(
                txHash = destinationTxHash,
                chainId = destinationChainId,
                operation = "bridge_complete",
                toAddress = recipient,
                amount = amount,
                currency = "", // Derived from transfer
                status = "confirmed",
                confirmedAt = Instant.now(),
                metadata = mapOf("transferId" to transferId).toString()
            )
        )
    }

    /**
     * Get pending bridge transfers (for monitoring/reconciliation).
     */
    fun getPendingBridgeTransfers(chainId: Long): List<BlockchainTransaction> {
        return txRepository.findPendingBridgeTransfers(chainId)
    }

    // ============ Helper Methods ============

    private fun generateTransferId(
        chainId: Long,
        fromAddress: String,
        toAddress: String,
        amount: BigDecimal
    ): String {
        val data = "$chainId:$fromAddress:$toAddress:$amount:${System.currentTimeMillis()}"
        return UUID.nameUUIDFromBytes(data.toByteArray()).toString()
    }

    private fun chainIdToBytes32(chainId: Long): ByteArray {
        val bytes = ByteArray(32)
        val chainIdBytes = BigInteger.valueOf(chainId).toByteArray()
        System.arraycopy(chainIdBytes, 0, bytes, 32 - chainIdBytes.size, chainIdBytes.size)
        return bytes
    }
}

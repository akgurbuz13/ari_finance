package com.ari.blockchain.relayer

import com.ari.blockchain.config.BlockchainConfig
import com.ari.blockchain.config.Web3jProvider
import com.ari.blockchain.repository.BlockchainTransaction
import com.ari.blockchain.repository.BlockchainTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

@Service
class GaslessRelayerService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val txRepository: BlockchainTransactionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class RelayResult(
        val txHash: String,
        val success: Boolean,
        val gasUsed: Long? = null,
        val error: String? = null
    )

    /**
     * Relays a meta-transaction (ERC-2771) on behalf of the user.
     * The relayer pays gas fees so users never need to hold native tokens.
     *
     * Flow:
     * 1. Verify the user's signature over the ForwardRequest
     * 2. Pack the ForwardRequest struct
     * 3. Submit to the Trusted Forwarder contract using the relayer's key
     * 4. Relayer pays gas; user's address is appended to calldata per ERC-2771
     */
    fun relayTransaction(
        forwarderAddress: String,
        targetContract: String,
        encodedFunctionCall: ByteArray,
        userAddress: String,
        userSignature: ByteArray,
        chainId: Long? = null
    ): RelayResult {
        log.info("Relaying meta-transaction for user={} to contract={}", userAddress, targetContract)

        val resolvedChainId = chainId ?: config.getChainId()
        val web3j = web3jProvider.getWeb3j(resolvedChainId)
        val relayerCredentials = Credentials.create(config.walletMasterKey)
        val txManager = RawTransactionManager(web3j, relayerCredentials, resolvedChainId)

        try {
            // Build the execute(ForwardRequest, signature) call to the Trusted Forwarder
            // ForwardRequest: { from, to, value, gas, nonce, data }
            val nonce = web3j.ethGetTransactionCount(
                userAddress,
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send().transactionCount

            val executeFunction = Function(
                "execute",
                listOf(
                    // ForwardRequest tuple encoded as individual params
                    Address(userAddress),            // from
                    Address(targetContract),         // to
                    Uint256(BigInteger.ZERO),         // value
                    Uint256(BigInteger.valueOf(200000)), // gas limit
                    Uint256(nonce),                   // nonce
                    DynamicBytes(encodedFunctionCall)  // data
                ),
                emptyList()
            )

            val encodedForwarderCall = FunctionEncoder.encode(executeFunction)
            val gasProvider = web3jProvider.getGasProvider()

            val response = txManager.sendTransaction(
                gasProvider.getGasPrice(encodedForwarderCall),
                gasProvider.getGasLimit(encodedForwarderCall),
                forwarderAddress,
                encodedForwarderCall,
                BigInteger.ZERO
            )

            if (response.hasError()) {
                throw RuntimeException("Relay transaction failed: ${response.error.message}")
            }

            val receipt = web3j.ethGetTransactionReceipt(response.transactionHash)
                .send()
                .transactionReceipt
                .orElseThrow { RuntimeException("No receipt for relay tx: ${response.transactionHash}") }

            val txHash = receipt.transactionHash
            val gasUsed = receipt.gasUsed.toLong()

            txRepository.save(
                BlockchainTransaction(
                    txHash = txHash,
                    chainId = resolvedChainId,
                    operation = "relay",
                    fromAddress = userAddress,
                    toAddress = targetContract,
                    amount = BigDecimal.ZERO,
                    currency = "N/A",
                    status = if (receipt.isStatusOK) "confirmed" else "failed",
                    blockNumber = receipt.blockNumber.toLong(),
                    gasUsed = gasUsed,
                    confirmedAt = Instant.now()
                )
            )

            log.info("Meta-transaction relayed: txHash={}, gasUsed={}", txHash, gasUsed)
            return RelayResult(txHash = txHash, success = receipt.isStatusOK, gasUsed = gasUsed)

        } catch (e: Exception) {
            log.error("Relay failed for user={}: {}", userAddress, e.message, e)
            return RelayResult(txHash = "", success = false, error = e.message)
        }
    }
}

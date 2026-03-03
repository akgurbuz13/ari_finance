package com.ova.blockchain.contract

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

/**
 * Type-safe wrapper for AriTokenHome.sol contract interactions via web3j.
 *
 * AriTokenHome is deployed on the "home" chain where the native token lives.
 * It locks tokens when bridging out and releases tokens when receiving bridged-back tokens.
 *
 * For ARI:
 * - TR L1 has TokenHome for ariTRY
 * - EU L1 has TokenHome for ariEUR
 */
class AriTokenHomeContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

    /**
     * Bridge tokens to a destination chain.
     * Locks tokens in this contract and sends a Teleporter message to mint on destination.
     *
     * @param destinationChainId The destination blockchain ID (bytes32)
     * @param recipient The recipient address on the destination chain
     * @param amount The amount of tokens to bridge
     * @param feeAmount Fee to pay to relayers
     * @return Transaction receipt
     */
    fun bridgeTokens(
        destinationChainId: ByteArray,
        recipient: String,
        amount: BigInteger,
        feeAmount: BigInteger
    ): TransactionReceipt {
        val function = Function(
            "bridgeTokens",
            listOf(
                Bytes32(destinationChainId),
                Address(recipient),
                Uint256(amount),
                Uint256(feeAmount)
            ),
            listOf(object : TypeReference<Bytes32>() {}) // returns messageID
        )
        return executeTransaction(function)
    }

    /**
     * Register a TokenRemote contract on a destination chain.
     * Only callable by BRIDGE_ADMIN_ROLE.
     */
    fun registerRemote(destinationChainId: ByteArray, remoteAddress: String): TransactionReceipt {
        val function = Function(
            "registerRemote",
            listOf(
                Bytes32(destinationChainId),
                Address(remoteAddress)
            ),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Unregister a TokenRemote contract.
     * Only callable by BRIDGE_ADMIN_ROLE.
     */
    fun unregisterRemote(destinationChainId: ByteArray): TransactionReceipt {
        val function = Function(
            "unregisterRemote",
            listOf(Bytes32(destinationChainId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Pause the bridge.
     * Only callable by PAUSE_ROLE.
     */
    fun pause(): TransactionReceipt {
        val function = Function("pause", emptyList(), emptyList())
        return executeTransaction(function)
    }

    /**
     * Unpause the bridge.
     * Only callable by PAUSE_ROLE.
     */
    fun unpause(): TransactionReceipt {
        val function = Function("unpause", emptyList(), emptyList())
        return executeTransaction(function)
    }

    /**
     * Update bridge limits.
     * Only callable by BRIDGE_ADMIN_ROLE.
     */
    fun setLimits(minAmount: BigInteger, maxAmount: BigInteger, dailyLimit: BigInteger): TransactionReceipt {
        val function = Function(
            "setLimits",
            listOf(
                Uint256(minAmount),
                Uint256(maxAmount),
                Uint256(dailyLimit)
            ),
            emptyList()
        )
        return executeTransaction(function)
    }

    // ============ View Functions ============

    /**
     * Check if the bridge is paused.
     */
    fun paused(): Boolean {
        val function = Function(
            "paused",
            emptyList(),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
    }

    /**
     * Get the total amount of tokens currently locked (bridged out).
     */
    fun totalBridgedOut(): BigInteger {
        val function = Function(
            "totalBridgedOut",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get the remaining daily limit for bridge transfers.
     */
    fun getRemainingDailyLimit(): BigInteger {
        val function = Function(
            "getRemainingDailyLimit",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get today's total bridged amount.
     */
    fun getTodayBridgedAmount(): BigInteger {
        val function = Function(
            "getTodayBridgedAmount",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get the minimum bridge amount.
     */
    fun minBridgeAmount(): BigInteger {
        val function = Function(
            "minBridgeAmount",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get the maximum bridge amount per transaction.
     */
    fun maxBridgeAmount(): BigInteger {
        val function = Function(
            "maxBridgeAmount",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get the daily limit for bridge transfers.
     */
    fun dailyLimit(): BigInteger {
        val function = Function(
            "dailyLimit",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get the registered remote address for a destination chain.
     */
    fun registeredRemotes(destinationChainId: ByteArray): String {
        val function = Function(
            "registeredRemotes",
            listOf(Bytes32(destinationChainId)),
            listOf(object : TypeReference<Address>() {})
        )
        return executeCall(function) as String
    }

    private fun executeTransaction(function: Function): TransactionReceipt {
        val encodedFunction = FunctionEncoder.encode(function)
        val response = txManager.sendTransaction(
            gasProvider.getGasPrice(encodedFunction),
            gasProvider.getGasLimit(encodedFunction),
            contractAddress,
            encodedFunction,
            BigInteger.ZERO
        )

        if (response.hasError()) {
            throw RuntimeException("Transaction failed: ${response.error.message}")
        }

        // Poll for receipt with retries (Avalanche L1s may need a moment to mine)
        val txHash = response.transactionHash
        var receipt: TransactionReceipt? = null
        for (i in 1..30) {
            receipt = web3j.ethGetTransactionReceipt(txHash)
                .send()
                .transactionReceipt
                .orElse(null)
            if (receipt != null) break
            Thread.sleep(1000)
        }

        if (receipt == null) {
            throw RuntimeException("No receipt for tx after 30s: $txHash")
        }

        if (!receipt.isStatusOK) {
            throw RuntimeException("Transaction reverted: ${receipt.transactionHash}")
        }

        return receipt
    }

    private fun executeCall(function: Function): Any {
        val encodedFunction = FunctionEncoder.encode(function)
        val response = web3j.ethCall(
            Transaction.createEthCallTransaction(
                credentials.address,
                contractAddress,
                encodedFunction
            ),
            DefaultBlockParameterName.LATEST
        ).send()

        if (response.hasError()) {
            throw RuntimeException("Call failed: ${response.error.message}")
        }

        val output = FunctionReturnDecoder.decode(response.value, function.outputParameters)
        return output.first().value
    }
}

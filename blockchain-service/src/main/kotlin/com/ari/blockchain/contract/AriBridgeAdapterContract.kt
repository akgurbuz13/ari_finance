package com.ari.blockchain.contract

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
 * Type-safe wrapper for AriBridgeAdapter.sol contract interactions via web3j.
 */
class AriBridgeAdapterContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

    /**
     * Bridge native tokens to partner chain.
     * Maps to Solidity: bridgeNativeTokens(address recipient, uint256 amount, uint256 feeAmount) returns (bytes32)
     *
     * This function:
     * 1. Transfers native tokens from msg.sender to adapter
     * 2. Calls TokenHome.bridgeTokens to lock tokens
     * 3. Emits Teleporter message to destination chain
     */
    fun bridgeNativeTokens(recipient: String, amount: BigInteger, feeAmount: BigInteger): TransactionReceipt {
        val function = Function(
            "bridgeNativeTokens",
            listOf(
                Address(recipient),
                Uint256(amount),
                Uint256(feeAmount)
            ),
            listOf(object : TypeReference<Bytes32>() {}) // returns transferId
        )
        return executeTransaction(function)
    }

    /**
     * Bridge wrapped tokens back to their home chain.
     * Maps to Solidity: bridgeWrappedTokensBack(address recipient, uint256 amount, uint256 feeAmount) returns (bytes32)
     *
     * This function:
     * 1. Burns wrapped tokens from msg.sender
     * 2. Calls TokenRemote.bridgeBack
     * 3. Sends Teleporter message to unlock on home chain
     */
    fun bridgeWrappedTokensBack(recipient: String, amount: BigInteger, feeAmount: BigInteger): TransactionReceipt {
        val function = Function(
            "bridgeWrappedTokensBack",
            listOf(
                Address(recipient),
                Uint256(amount),
                Uint256(feeAmount)
            ),
            listOf(object : TypeReference<Bytes32>() {}) // returns transferId
        )
        return executeTransaction(function)
    }

    /**
     * Mark a transfer as completed (operator only).
     * Maps to Solidity: markTransferCompleted(bytes32 transferId)
     */
    fun markTransferCompleted(transferId: ByteArray): TransactionReceipt {
        val function = Function(
            "markTransferCompleted",
            listOf(Bytes32(transferId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Mark a transfer as failed (operator only).
     * Maps to Solidity: markTransferFailed(bytes32 transferId)
     */
    fun markTransferFailed(transferId: ByteArray): TransactionReceipt {
        val function = Function(
            "markTransferFailed",
            listOf(Bytes32(transferId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Check if adapter is paused.
     */
    fun paused(): Boolean {
        val function = Function(
            "paused",
            emptyList(),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
    }

    // Legacy methods kept for compatibility (may be removed in future)

    @Deprecated("Use bridgeNativeTokens instead", ReplaceWith("bridgeNativeTokens(recipient, amount, BigInteger.ZERO)"))
    fun sendTokens(amount: BigInteger, destinationChainId: ByteArray, recipient: String): TransactionReceipt {
        val function = Function(
            "sendTokens",
            listOf(
                Uint256(amount),
                Bytes32(destinationChainId),
                Address(recipient)
            ),
            emptyList()
        )
        return executeTransaction(function)
    }

    @Deprecated("Use markTransferCompleted instead")
    fun receiveTokens(messageId: ByteArray, recipient: String, amount: BigInteger): TransactionReceipt {
        val function = Function(
            "receiveTokens",
            listOf(
                Bytes32(messageId),
                Address(recipient),
                Uint256(amount)
            ),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun processedMessages(messageId: ByteArray): Boolean {
        val function = Function(
            "processedMessages",
            listOf(Bytes32(messageId)),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
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

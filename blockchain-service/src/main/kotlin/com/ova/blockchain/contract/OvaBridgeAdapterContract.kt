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
 * Type-safe wrapper for OvaBridgeAdapter.sol contract interactions via web3j.
 */
class OvaBridgeAdapterContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

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

        val receipt = web3j.ethGetTransactionReceipt(response.transactionHash)
            .send()
            .transactionReceipt
            .orElseThrow { RuntimeException("No receipt for tx: ${response.transactionHash}") }

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

package com.ari.blockchain.contract

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
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
 * Type-safe wrapper for AriVehicleNFT.sol contract interactions via web3j.
 */
class AriVehicleNFTContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

    fun mint(to: String, vinHash: ByteArray, plateHash: ByteArray, metadataUri: String): TransactionReceipt {
        val function = Function(
            "mint",
            listOf(Address(to), Bytes32(vinHash), Bytes32(plateHash), Utf8String(metadataUri)),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeTransaction(function)
    }

    fun approve(to: String, tokenId: BigInteger): TransactionReceipt {
        val function = Function(
            "approve",
            listOf(Address(to), Uint256(tokenId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun ownerOf(tokenId: BigInteger): String {
        val function = Function(
            "ownerOf",
            listOf(Uint256(tokenId)),
            listOf(object : TypeReference<Address>() {})
        )
        return executeCall(function) as String
    }

    fun addToAllowlist(account: String): TransactionReceipt {
        val function = Function(
            "addToAllowlist",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun kycAllowlisted(account: String): Boolean {
        val function = Function(
            "kycAllowlisted",
            listOf(Address(account)),
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

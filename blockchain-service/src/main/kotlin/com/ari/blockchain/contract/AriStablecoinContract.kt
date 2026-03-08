package com.ari.blockchain.contract

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
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
 * Type-safe wrapper for AriStablecoin.sol contract interactions via web3j.
 */
class AriStablecoinContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

    fun mint(to: String, amount: BigInteger): TransactionReceipt {
        val function = Function(
            "mint",
            listOf(Address(to), Uint256(amount)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun burn(from: String, amount: BigInteger): TransactionReceipt {
        val function = Function(
            "burn",
            listOf(Address(from), Uint256(amount)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun transfer(to: String, amount: BigInteger): TransactionReceipt {
        val function = Function(
            "transfer",
            listOf(Address(to), Uint256(amount)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun totalSupply(): BigInteger {
        val function = Function(
            "totalSupply",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    fun balanceOf(account: String): BigInteger {
        val function = Function(
            "balanceOf",
            listOf(Address(account)),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    fun allowlisted(account: String): Boolean {
        val function = Function(
            "allowlisted",
            listOf(Address(account)),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
    }

    fun addToAllowlist(account: String): TransactionReceipt {
        val function = Function(
            "addToAllowlist",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun batchAddToAllowlist(accounts: List<String>): TransactionReceipt {
        val function = Function(
            "batchAddToAllowlist",
            listOf(org.web3j.abi.datatypes.DynamicArray(
                Address::class.java,
                accounts.map { Address(it) }
            )),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun freeze(account: String): TransactionReceipt {
        val function = Function(
            "freeze",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun unfreeze(account: String): TransactionReceipt {
        val function = Function(
            "unfreeze",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
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

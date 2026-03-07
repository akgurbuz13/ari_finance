package com.ova.blockchain.contract

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

/**
 * Type-safe wrapper for AriVehicleEscrow.sol contract interactions via web3j.
 */
class AriVehicleEscrowContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

    fun createEscrow(tokenId: BigInteger, seller: String, buyer: String, saleAmount: BigInteger): TransactionReceipt {
        val function = Function(
            "createEscrow",
            listOf(Uint256(tokenId), Address(seller), Address(buyer), Uint256(saleAmount)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun fundEscrow(escrowId: BigInteger): TransactionReceipt {
        val function = Function(
            "fundEscrow",
            listOf(Uint256(escrowId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun sellerConfirm(escrowId: BigInteger): TransactionReceipt {
        val function = Function(
            "sellerConfirm",
            listOf(Uint256(escrowId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun buyerConfirm(escrowId: BigInteger): TransactionReceipt {
        val function = Function(
            "buyerConfirm",
            listOf(Uint256(escrowId)),
            emptyList()
        )
        return executeTransaction(function)
    }

    fun cancel(escrowId: BigInteger): TransactionReceipt {
        val function = Function(
            "cancel",
            listOf(Uint256(escrowId)),
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
}

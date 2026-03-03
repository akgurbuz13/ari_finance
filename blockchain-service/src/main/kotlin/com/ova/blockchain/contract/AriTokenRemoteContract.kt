package com.ova.blockchain.contract

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicArray
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
 * Type-safe wrapper for AriTokenRemote.sol contract interactions via web3j.
 *
 * AriTokenRemote is deployed on "remote" chains where wrapped tokens are minted.
 * It mints wrapped tokens when receiving from home chain and burns when sending back.
 *
 * For ARI:
 * - On TR L1: This wraps EUR tokens from EU L1 as "wEUR"
 * - On EU L1: This wraps TRY tokens from TR L1 as "wTRY"
 *
 * WRAPPED TOKEN PROPERTIES:
 * - Fully backed 1:1 by locked tokens on the home chain
 * - Can only be minted by valid cross-chain messages
 * - Burning sends tokens back to home chain
 * - Inherits KYC requirements from the platform
 */
class AriTokenRemoteContract(
    private val web3j: Web3j,
    private val contractAddress: String,
    private val credentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long
) {
    private val txManager = RawTransactionManager(web3j, credentials, chainId)

    /**
     * Burn wrapped tokens and bridge back to the home chain.
     * User must be KYC verified (allowlisted) and not frozen.
     *
     * @param recipient The recipient address on the home chain
     * @param amount The amount to burn and bridge back
     * @param feeAmount Fee to pay to relayers
     * @return Transaction receipt
     */
    fun bridgeBack(recipient: String, amount: BigInteger, feeAmount: BigInteger): TransactionReceipt {
        val function = Function(
            "bridgeBack",
            listOf(
                Address(recipient),
                Uint256(amount),
                Uint256(feeAmount)
            ),
            listOf(object : TypeReference<Bytes32>() {}) // returns messageID
        )
        return executeTransaction(function)
    }

    /**
     * Register the home chain and TokenHome address.
     * Only callable by BRIDGE_ADMIN_ROLE.
     */
    fun registerHomeChain(homeChainId: ByteArray, tokenHomeAddress: String): TransactionReceipt {
        val function = Function(
            "registerHomeChain",
            listOf(
                Bytes32(homeChainId),
                Address(tokenHomeAddress)
            ),
            emptyList()
        )
        return executeTransaction(function)
    }

    // ============ KYC Management ============

    /**
     * Add an address to the KYC allowlist.
     * Only callable by DEFAULT_ADMIN_ROLE.
     */
    fun addToAllowlist(account: String): TransactionReceipt {
        val function = Function(
            "addToAllowlist",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Remove an address from the KYC allowlist.
     * Only callable by DEFAULT_ADMIN_ROLE.
     */
    fun removeFromAllowlist(account: String): TransactionReceipt {
        val function = Function(
            "removeFromAllowlist",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Add multiple addresses to the KYC allowlist.
     * Only callable by DEFAULT_ADMIN_ROLE.
     */
    fun batchAddToAllowlist(accounts: List<String>): TransactionReceipt {
        val function = Function(
            "batchAddToAllowlist",
            listOf(
                DynamicArray(
                    Address::class.java,
                    accounts.map { Address(it) }
                )
            ),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Freeze an address (prevent transfers).
     * Only callable by FREEZER_ROLE.
     */
    fun freeze(account: String): TransactionReceipt {
        val function = Function(
            "freeze",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    /**
     * Unfreeze an address.
     * Only callable by FREEZER_ROLE.
     */
    fun unfreeze(account: String): TransactionReceipt {
        val function = Function(
            "unfreeze",
            listOf(Address(account)),
            emptyList()
        )
        return executeTransaction(function)
    }

    // ============ Bridge Control ============

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

    // ============ ERC20 Functions ============

    /**
     * Get the total supply of wrapped tokens.
     */
    fun totalSupply(): BigInteger {
        val function = Function(
            "totalSupply",
            emptyList(),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Get the balance of an account.
     */
    fun balanceOf(account: String): BigInteger {
        val function = Function(
            "balanceOf",
            listOf(Address(account)),
            listOf(object : TypeReference<Uint256>() {})
        )
        return executeCall(function) as BigInteger
    }

    /**
     * Transfer wrapped tokens to another address.
     * Both sender and recipient must be KYC verified.
     */
    fun transfer(to: String, amount: BigInteger): TransactionReceipt {
        val function = Function(
            "transfer",
            listOf(Address(to), Uint256(amount)),
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
     * Check if an address is KYC allowlisted.
     */
    fun allowlisted(account: String): Boolean {
        val function = Function(
            "allowlisted",
            listOf(Address(account)),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
    }

    /**
     * Check if an address is frozen.
     */
    fun frozen(account: String): Boolean {
        val function = Function(
            "frozen",
            listOf(Address(account)),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
    }

    /**
     * Check if an address can hold wrapped tokens (KYC verified and not frozen).
     */
    fun canHold(account: String): Boolean {
        val function = Function(
            "canHold",
            listOf(Address(account)),
            listOf(object : TypeReference<Bool>() {})
        )
        return executeCall(function) as Boolean
    }

    /**
     * Get the home chain ID.
     */
    fun homeChainID(): ByteArray {
        val function = Function(
            "homeChainID",
            emptyList(),
            listOf(object : TypeReference<Bytes32>() {})
        )
        return executeCall(function) as ByteArray
    }

    /**
     * Get the TokenHome contract address on the home chain.
     */
    fun tokenHomeAddress(): String {
        val function = Function(
            "tokenHomeAddress",
            emptyList(),
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

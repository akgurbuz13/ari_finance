package com.ari.blockchain.settlement

import com.ari.blockchain.config.BlockchainConfig
import com.ari.blockchain.config.Web3jProvider
import com.ari.blockchain.contract.ContractFactory
import com.ari.blockchain.repository.BlockchainTransaction
import com.ari.blockchain.repository.BlockchainTransactionRepository
import com.ari.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

@Service
class MintService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val contractFactory: ContractFactory,
    private val walletService: CustodialWalletService,
    private val txRepository: BlockchainTransactionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class MintResult(
        val txHash: String,
        val success: Boolean,
        val blockNumber: BigInteger? = null,
        val error: String? = null
    )

    fun mint(toAddress: String, amount: BigDecimal, currency: String, chainId: Long, paymentOrderId: UUID? = null): MintResult {
        return mintOnChain(toAddress, amount, currency, chainId, paymentOrderId)
    }

    fun mint(toAddress: String, amount: BigDecimal, currency: String, paymentOrderId: UUID? = null): MintResult {
        val chainId = web3jProvider.getChainIdForCurrency(currency)
        return mintOnChain(toAddress, amount, currency, chainId, paymentOrderId)
    }

    private fun mintOnChain(toAddress: String, amount: BigDecimal, currency: String, chainId: Long, paymentOrderId: UUID?): MintResult {
        log.info("Minting {} {} to address {} on chain {}", amount, currency, toAddress, chainId)
        val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
        val minterCredentials = walletService.getMinterCredentials()

        try {
            val stablecoin = contractFactory.getStablecoin(chainId, minterCredentials)

            // Ensure recipient is allowlisted before minting
            if (!stablecoin.allowlisted(toAddress)) {
                log.info("Adding {} to allowlist before mint", toAddress)
                stablecoin.addToAllowlist(toAddress)
            }

            val receipt = stablecoin.mint(toAddress, amountWei)

            val txHash = receipt.transactionHash
            val blockNumber = receipt.blockNumber

            // Persist transaction record
            txRepository.save(
                BlockchainTransaction(
                    txHash = txHash,
                    chainId = chainId,
                    operation = "mint",
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "confirmed",
                    blockNumber = blockNumber.toLong(),
                    gasUsed = receipt.gasUsed.toLong(),
                    paymentOrderId = paymentOrderId,
                    confirmedAt = Instant.now()
                )
            )

            log.info("Mint confirmed: txHash={}, block={}", txHash, blockNumber)
            return MintResult(txHash = txHash, success = true, blockNumber = blockNumber)

        } catch (e: Exception) {
            log.error("Mint failed for {} {} to {}: {}", amount, currency, toAddress, e.message, e)

            txRepository.save(
                BlockchainTransaction(
                    txHash = "failed-${UUID.randomUUID()}",
                    chainId = chainId,
                    operation = "mint",
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "failed",
                    paymentOrderId = paymentOrderId,
                    errorMessage = e.message
                )
            )

            return MintResult(txHash = "", success = false, error = e.message)
        }
    }
}

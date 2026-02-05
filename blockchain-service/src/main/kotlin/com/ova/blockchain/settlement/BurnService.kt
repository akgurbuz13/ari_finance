package com.ova.blockchain.settlement

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.contract.ContractFactory
import com.ova.blockchain.repository.BlockchainTransaction
import com.ova.blockchain.repository.BlockchainTransactionRepository
import com.ova.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

@Service
class BurnService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val contractFactory: ContractFactory,
    private val walletService: CustodialWalletService,
    private val txRepository: BlockchainTransactionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class BurnResult(
        val txHash: String,
        val success: Boolean,
        val blockNumber: BigInteger? = null,
        val error: String? = null
    )

    fun burn(fromAddress: String, amount: BigDecimal, currency: String, paymentOrderId: UUID? = null): BurnResult {
        log.info("Burning {} {} from address {}", amount, currency, fromAddress)

        val chainId = web3jProvider.getChainIdForCurrency(currency)
        val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
        val minterCredentials = walletService.getMinterCredentials()

        try {
            val stablecoin = contractFactory.getStablecoin(chainId, minterCredentials)
            val receipt = stablecoin.burn(fromAddress, amountWei)

            val txHash = receipt.transactionHash
            val blockNumber = receipt.blockNumber

            txRepository.save(
                BlockchainTransaction(
                    txHash = txHash,
                    chainId = chainId,
                    operation = "burn",
                    fromAddress = fromAddress,
                    amount = amount,
                    currency = currency,
                    status = "confirmed",
                    blockNumber = blockNumber.toLong(),
                    gasUsed = receipt.gasUsed.toLong(),
                    paymentOrderId = paymentOrderId,
                    confirmedAt = Instant.now()
                )
            )

            log.info("Burn confirmed: txHash={}, block={}", txHash, blockNumber)
            return BurnResult(txHash = txHash, success = true, blockNumber = blockNumber)

        } catch (e: Exception) {
            log.error("Burn failed for {} {} from {}: {}", amount, currency, fromAddress, e.message, e)

            txRepository.save(
                BlockchainTransaction(
                    txHash = "failed-${UUID.randomUUID()}",
                    chainId = chainId,
                    operation = "burn",
                    fromAddress = fromAddress,
                    amount = amount,
                    currency = currency,
                    status = "failed",
                    paymentOrderId = paymentOrderId,
                    errorMessage = e.message
                )
            )

            return BurnResult(txHash = "", success = false, error = e.message)
        }
    }
}

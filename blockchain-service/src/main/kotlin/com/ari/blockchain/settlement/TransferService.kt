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
import java.time.Instant
import java.util.UUID

@Service
class TransferService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val contractFactory: ContractFactory,
    private val walletService: CustodialWalletService,
    private val txRepository: BlockchainTransactionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class TransferResult(
        val txHash: String,
        val success: Boolean,
        val error: String? = null
    )

    fun transfer(
        fromAddress: String,
        toAddress: String,
        amount: BigDecimal,
        currency: String,
        paymentOrderId: UUID? = null
    ): TransferResult {
        log.info("Transferring {} {} from {} to {}", amount, currency, fromAddress, toAddress)

        val chainId = web3jProvider.getChainIdForCurrency(currency)
        val amountWei = amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()

        try {
            // Use minter credentials for system-initiated transfers
            // In user-initiated transfers, the gasless relayer would be used instead
            val credentials = walletService.getMinterCredentials()
            val stablecoin = contractFactory.getStablecoin(chainId, credentials)

            // Ensure both parties are allowlisted
            if (!stablecoin.allowlisted(toAddress)) {
                stablecoin.addToAllowlist(toAddress)
            }

            val receipt = stablecoin.transfer(toAddress, amountWei)

            val txHash = receipt.transactionHash

            txRepository.save(
                BlockchainTransaction(
                    txHash = txHash,
                    chainId = chainId,
                    operation = "transfer",
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "confirmed",
                    blockNumber = receipt.blockNumber.toLong(),
                    gasUsed = receipt.gasUsed.toLong(),
                    paymentOrderId = paymentOrderId,
                    confirmedAt = Instant.now()
                )
            )

            log.info("Transfer confirmed: txHash={}", txHash)
            return TransferResult(txHash = txHash, success = true)

        } catch (e: Exception) {
            log.error("Transfer failed from {} to {}: {}", fromAddress, toAddress, e.message, e)

            txRepository.save(
                BlockchainTransaction(
                    txHash = "failed-${UUID.randomUUID()}",
                    chainId = chainId,
                    operation = "transfer",
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amount,
                    currency = currency,
                    status = "failed",
                    paymentOrderId = paymentOrderId,
                    errorMessage = e.message
                )
            )

            return TransferResult(txHash = "", success = false, error = e.message)
        }
    }
}

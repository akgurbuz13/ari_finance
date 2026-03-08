package com.ari.blockchain.reconciliation

import com.ari.blockchain.config.BlockchainConfig
import com.ari.blockchain.config.Web3jProvider
import com.ari.blockchain.contract.ContractFactory
import com.ari.blockchain.wallet.CustodialWalletService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ReconciliationService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val contractFactory: ContractFactory,
    private val walletService: CustodialWalletService,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ReconciliationResult(
        val currency: String,
        val chainId: Long,
        val onChainTotal: BigDecimal,
        val offChainTotal: BigDecimal,
        val difference: BigDecimal,
        val matched: Boolean
    )

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    fun runDailyReconciliation() {
        log.info("Starting daily on-chain vs off-chain reconciliation")

        val currencies = listOf("TRY", "EUR")
        val results = currencies.map { reconcile(it) }

        for (result in results) {
            // Persist result
            persistResult(result)

            if (!result.matched) {
                log.error(
                    "RECONCILIATION MISMATCH: {} (chain {}) - on-chain={} off-chain={} diff={}",
                    result.currency, result.chainId, result.onChainTotal,
                    result.offChainTotal, result.difference
                )
            } else {
                log.info(
                    "Reconciliation OK: {} (chain {}) - total={}",
                    result.currency, result.chainId, result.onChainTotal
                )
            }
        }
    }

    fun reconcile(currency: String): ReconciliationResult {
        val chainId = web3jProvider.getChainIdForCurrency(currency)

        // Get off-chain total from ledger
        val offChainTotal = getOffChainTotal(currency)

        // Get on-chain total supply from stablecoin contract
        val onChainTotal = getOnChainTotalSupply(currency, chainId)

        val difference = onChainTotal.subtract(offChainTotal).abs()
        val threshold = BigDecimal("0.01")

        return ReconciliationResult(
            currency = currency,
            chainId = chainId,
            onChainTotal = onChainTotal,
            offChainTotal = offChainTotal,
            difference = difference,
            matched = difference <= threshold
        )
    }

    private fun getOffChainTotal(currency: String): BigDecimal {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(e.balance_after), 0)
                FROM ledger.entries e
                JOIN ledger.accounts a ON a.id = e.account_id
                WHERE a.currency = ? AND a.account_type = 'user_wallet'
                AND e.id IN (
                    SELECT MAX(id) FROM ledger.entries GROUP BY account_id
                )
                """,
                BigDecimal::class.java, currency
            ) ?: BigDecimal.ZERO
        } catch (e: Exception) {
            log.error("Failed to query off-chain total for {}: {}", currency, e.message)
            BigDecimal.ZERO
        }
    }

    private fun getOnChainTotalSupply(currency: String, chainId: Long): BigDecimal {
        return try {
            val stablecoinAddress = web3jProvider.getStablecoinAddress(chainId)
            if (stablecoinAddress.isBlank()) {
                log.debug("No stablecoin address configured for chain {}", chainId)
                return BigDecimal.ZERO
            }

            val credentials = walletService.getMinterCredentials()
            val stablecoin = contractFactory.getStablecoin(chainId, credentials)
            val totalSupplyWei = stablecoin.totalSupply()

            // Convert from wei (18 decimals) to human-readable
            BigDecimal(totalSupplyWei).divide(BigDecimal.TEN.pow(18))
        } catch (e: Exception) {
            log.error("Failed to query on-chain totalSupply for {} (chain {}): {}",
                currency, chainId, e.message)
            BigDecimal.ZERO
        }
    }

    private fun persistResult(result: ReconciliationResult) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO blockchain.reconciliations
                    (chain_id, currency, on_chain_supply, off_chain_total, difference, matched, reconciliation_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (chain_id, currency, reconciliation_date)
                DO UPDATE SET
                    on_chain_supply = EXCLUDED.on_chain_supply,
                    off_chain_total = EXCLUDED.off_chain_total,
                    difference = EXCLUDED.difference,
                    matched = EXCLUDED.matched
                """,
                result.chainId, result.currency, result.onChainTotal,
                result.offChainTotal, result.difference, result.matched,
                java.sql.Date.valueOf(LocalDate.now())
            )
        } catch (e: Exception) {
            log.error("Failed to persist reconciliation result for {}: {}", result.currency, e.message)
        }
    }
}

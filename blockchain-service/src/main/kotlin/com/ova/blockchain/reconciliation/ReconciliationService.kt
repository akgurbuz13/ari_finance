package com.ova.blockchain.reconciliation

import com.ova.blockchain.config.BlockchainConfig
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ReconciliationService(
    private val config: BlockchainConfig,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ReconciliationResult(
        val currency: String,
        val onChainTotal: BigDecimal,
        val offChainTotal: BigDecimal,
        val difference: BigDecimal,
        val matched: Boolean
    )

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    fun runDailyReconciliation() {
        log.info("Starting daily reconciliation")

        val currencies = listOf("TRY", "EUR")
        val results = currencies.map { reconcile(it) }

        for (result in results) {
            if (!result.matched) {
                log.error(
                    "RECONCILIATION MISMATCH: {} - on-chain={} off-chain={} diff={}",
                    result.currency, result.onChainTotal, result.offChainTotal, result.difference
                )
                // TODO: Send alert to ops team
            } else {
                log.info(
                    "Reconciliation OK: {} - total={}",
                    result.currency, result.onChainTotal
                )
            }
        }
    }

    fun reconcile(currency: String): ReconciliationResult {
        // Get off-chain total from ledger
        val offChainTotal = getOffChainTotal(currency)

        // Get on-chain total supply from stablecoin contract
        val onChainTotal = getOnChainTotalSupply(currency)

        val difference = onChainTotal.subtract(offChainTotal).abs()
        val threshold = BigDecimal("0.01") // Allow tiny rounding differences

        return ReconciliationResult(
            currency = currency,
            onChainTotal = onChainTotal,
            offChainTotal = offChainTotal,
            difference = difference,
            matched = difference <= threshold
        )
    }

    private fun getOffChainTotal(currency: String): BigDecimal {
        // Sum all user wallet balances from the ledger
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
            log.error("Failed to query off-chain total for {}", currency, e)
            BigDecimal.ZERO
        }
    }

    private fun getOnChainTotalSupply(currency: String): BigDecimal {
        // TODO: Query totalSupply() from stablecoin contract via web3j
        // Stub: return zero for now
        return BigDecimal.ZERO
    }
}

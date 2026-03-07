package com.ova.blockchain.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.StaticGasProvider
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

@Component
class Web3jProvider(
    private val config: BlockchainConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val instances = ConcurrentHashMap<Long, Web3j>()

    fun getWeb3j(chainId: Long): Web3j {
        return instances.computeIfAbsent(chainId) { id ->
            val rpcUrl = getRpcUrl(id)
            log.info("Connecting to chain {} at {}", id, rpcUrl)
            Web3j.build(HttpService(rpcUrl))
        }
    }

    fun getWeb3jForRegion(): Web3j {
        return getWeb3j(config.getChainId())
    }

    fun getTrWeb3j(): Web3j = getWeb3j(config.trL1ChainId)
    fun getEuWeb3j(): Web3j = getWeb3j(config.euL1ChainId)

    private fun getRpcUrl(chainId: Long): String {
        return when (chainId) {
            config.trL1ChainId -> config.trL1RpcUrl
            config.euL1ChainId -> config.euL1RpcUrl
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    fun getStablecoinAddress(chainId: Long): String {
        return when (chainId) {
            config.trL1ChainId -> config.trStablecoinAddress
            config.euL1ChainId -> config.euStablecoinAddress
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    fun getStablecoinAddress(chainId: Long, currency: String): String {
        return config.getStablecoinAddress(chainId, currency)
    }

    fun getChainIdForCurrency(currency: String): Long {
        return when (currency) {
            "TRY" -> config.trL1ChainId
            "EUR" -> config.euL1ChainId
            else -> throw IllegalArgumentException("Unsupported currency: $currency")
        }
    }

    fun getGasProvider() = StaticGasProvider(
        BigInteger.valueOf(25_000_000_000L), // 25 gwei gas price
        BigInteger.valueOf(5_000_000L)       // 5M gas limit (under 8M block limit)
    )
}

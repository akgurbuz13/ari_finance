package com.ova.blockchain.contract

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import org.springframework.stereotype.Component
import org.web3j.crypto.Credentials
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating chain-specific contract instances.
 * Caches contract instances per chain to avoid redundant initialization.
 */
@Component
class ContractFactory(
    private val web3jProvider: Web3jProvider,
    private val config: BlockchainConfig
) {
    private val stablecoinContracts = ConcurrentHashMap<Long, OvaStablecoinContract>()
    private val bridgeContracts = ConcurrentHashMap<Long, OvaBridgeAdapterContract>()

    fun getStablecoin(chainId: Long, credentials: Credentials): OvaStablecoinContract {
        val key = chainId
        return stablecoinContracts.computeIfAbsent(key) {
            OvaStablecoinContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = web3jProvider.getStablecoinAddress(chainId),
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    fun getStablecoinForCurrency(currency: String, credentials: Credentials): OvaStablecoinContract {
        val chainId = web3jProvider.getChainIdForCurrency(currency)
        return getStablecoin(chainId, credentials)
    }

    fun getBridgeAdapter(chainId: Long, credentials: Credentials): OvaBridgeAdapterContract {
        return bridgeContracts.computeIfAbsent(chainId) {
            OvaBridgeAdapterContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = config.bridgeContractAddress,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }
}

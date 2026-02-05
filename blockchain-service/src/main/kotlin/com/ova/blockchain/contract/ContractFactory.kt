package com.ova.blockchain.contract

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import org.springframework.stereotype.Component
import org.web3j.crypto.Credentials
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating chain-specific contract instances.
 * Caches contract instances per chain to avoid redundant initialization.
 *
 * Contract types:
 * - OvaStablecoin: The native stablecoin on each chain (ovaTRY on TR, ovaEUR on EU)
 * - OvaBridgeAdapter: High-level bridge orchestration contract
 * - OvaTokenHome: ICTT TokenHome - locks native tokens when bridging out
 * - OvaTokenRemote: ICTT TokenRemote - mints/burns wrapped tokens from partner chain
 */
@Component
class ContractFactory(
    private val web3jProvider: Web3jProvider,
    private val config: BlockchainConfig
) {
    private val stablecoinContracts = ConcurrentHashMap<Long, OvaStablecoinContract>()
    private val bridgeContracts = ConcurrentHashMap<Long, OvaBridgeAdapterContract>()
    private val tokenHomeContracts = ConcurrentHashMap<Long, OvaTokenHomeContract>()
    private val tokenRemoteContracts = ConcurrentHashMap<Long, OvaTokenRemoteContract>()

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

    /**
     * Get TokenHome contract for a chain.
     * TokenHome is deployed on the chain where the native token exists.
     * - TR L1: TokenHome for ovaTRY (locks TRY when bridging to EU)
     * - EU L1: TokenHome for ovaEUR (locks EUR when bridging to TR)
     */
    fun getTokenHome(chainId: Long, credentials: Credentials): OvaTokenHomeContract {
        return tokenHomeContracts.computeIfAbsent(chainId) {
            val address = config.getTokenHomeAddress(chainId)
            require(address.isNotBlank()) { "TokenHome address not configured for chain $chainId" }
            OvaTokenHomeContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    /**
     * Get TokenRemote contract for a chain.
     * TokenRemote is deployed on the chain where wrapped tokens are minted.
     * - TR L1: TokenRemote for wEUR (wrapped EUR from EU L1)
     * - EU L1: TokenRemote for wTRY (wrapped TRY from TR L1)
     */
    fun getTokenRemote(chainId: Long, credentials: Credentials): OvaTokenRemoteContract {
        return tokenRemoteContracts.computeIfAbsent(chainId) {
            val address = config.getTokenRemoteAddress(chainId)
            require(address.isNotBlank()) { "TokenRemote address not configured for chain $chainId" }
            OvaTokenRemoteContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    /**
     * Get TokenHome contract for a specific currency.
     */
    fun getTokenHomeForCurrency(currency: String, credentials: Credentials): OvaTokenHomeContract {
        val chainId = web3jProvider.getChainIdForCurrency(currency)
        return getTokenHome(chainId, credentials)
    }

    /**
     * Get TokenRemote contract for wrapped tokens of a specific currency.
     * Note: This returns the remote on the DESTINATION chain.
     * e.g., for "TRY" currency, returns the wTRY contract on EU L1.
     */
    fun getTokenRemoteForWrappedCurrency(currency: String, credentials: Credentials): OvaTokenRemoteContract {
        // Wrapped tokens exist on the PARTNER chain
        val homeChainId = web3jProvider.getChainIdForCurrency(currency)
        val remoteChainId = config.getPartnerChainId(homeChainId)
        return getTokenRemote(remoteChainId, credentials)
    }
}

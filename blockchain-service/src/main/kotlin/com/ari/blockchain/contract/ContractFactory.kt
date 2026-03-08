package com.ari.blockchain.contract

import com.ari.blockchain.config.BlockchainConfig
import com.ari.blockchain.config.Web3jProvider
import org.springframework.stereotype.Component
import org.web3j.crypto.Credentials
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating chain-specific contract instances.
 * Caches contract instances per chain to avoid redundant initialization.
 *
 * Contract types:
 * - AriStablecoin: The native stablecoin on each chain (ariTRY on TR, ariEUR on EU)
 * - AriBridgeAdapter: High-level bridge orchestration contract
 * - AriTokenHome: ICTT TokenHome - locks native tokens when bridging out
 * - AriTokenRemote: ICTT TokenRemote - mints/burns wrapped tokens from partner chain
 */
@Component
class ContractFactory(
    private val web3jProvider: Web3jProvider,
    private val config: BlockchainConfig
) {
    private val stablecoinContracts = ConcurrentHashMap<Long, AriStablecoinContract>()
    private val stablecoinByChainCurrency = ConcurrentHashMap<Pair<Long, String>, AriStablecoinContract>()
    private val bridgeContracts = ConcurrentHashMap<Long, AriBridgeAdapterContract>()
    private val burnMintBridgeContracts = ConcurrentHashMap<Long, AriBurnMintBridgeContract>()
    private val tokenHomeContracts = ConcurrentHashMap<Long, AriTokenHomeContract>()
    private val tokenRemoteContracts = ConcurrentHashMap<Long, AriTokenRemoteContract>()
    private var vehicleNftContract: AriVehicleNFTContract? = null
    private var vehicleEscrowContract: AriVehicleEscrowContract? = null

    fun getStablecoin(chainId: Long, credentials: Credentials): AriStablecoinContract {
        val key = chainId
        return stablecoinContracts.computeIfAbsent(key) {
            AriStablecoinContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = web3jProvider.getStablecoinAddress(chainId),
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    fun getStablecoinForCurrency(currency: String, credentials: Credentials): AriStablecoinContract {
        val chainId = web3jProvider.getChainIdForCurrency(currency)
        return getStablecoin(chainId, credentials)
    }

    /**
     * Get stablecoin for a specific currency on a specific chain.
     * Supports cross-currency (e.g. ariTRY on EU L1).
     */
    fun getStablecoin(chainId: Long, currency: String, credentials: Credentials): AriStablecoinContract {
        val key = Pair(chainId, currency)
        return stablecoinByChainCurrency.computeIfAbsent(key) {
            AriStablecoinContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = config.getStablecoinAddress(chainId, currency),
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    /**
     * Get burn-mint bridge contract for a chain.
     */
    fun getBurnMintBridge(chainId: Long, credentials: Credentials): AriBurnMintBridgeContract {
        return burnMintBridgeContracts.computeIfAbsent(chainId) {
            val address = config.getBurnMintBridgeAddress(chainId)
            require(address.isNotBlank()) { "BurnMintBridge address not configured for chain $chainId" }
            AriBurnMintBridgeContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    fun getBridgeAdapter(chainId: Long, credentials: Credentials): AriBridgeAdapterContract {
        return bridgeContracts.computeIfAbsent(chainId) {
            val address = config.getBridgeAdapterAddress(chainId)
            require(address.isNotBlank()) { "BridgeAdapter address not configured for chain $chainId" }
            AriBridgeAdapterContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    /**
     * Get TokenHome contract for a chain.
     * TokenHome is deployed on the chain where the native token exists.
     * - TR L1: TokenHome for ariTRY (locks TRY when bridging to EU)
     * - EU L1: TokenHome for ariEUR (locks EUR when bridging to TR)
     */
    fun getTokenHome(chainId: Long, credentials: Credentials): AriTokenHomeContract {
        return tokenHomeContracts.computeIfAbsent(chainId) {
            val address = config.getTokenHomeAddress(chainId)
            require(address.isNotBlank()) { "TokenHome address not configured for chain $chainId" }
            AriTokenHomeContract(
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
    fun getTokenRemote(chainId: Long, credentials: Credentials): AriTokenRemoteContract {
        return tokenRemoteContracts.computeIfAbsent(chainId) {
            val address = config.getTokenRemoteAddress(chainId)
            require(address.isNotBlank()) { "TokenRemote address not configured for chain $chainId" }
            AriTokenRemoteContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            )
        }
    }

    /**
     * Get AriVehicleNFT contract (TR L1 only for MVP).
     */
    fun getVehicleNFT(credentials: Credentials): AriVehicleNFTContract {
        return vehicleNftContract ?: run {
            val address = config.vehicleNftAddress
            require(address.isNotBlank()) { "Vehicle NFT address not configured" }
            val chainId = config.trL1ChainId
            AriVehicleNFTContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            ).also { vehicleNftContract = it }
        }
    }

    /**
     * Get AriVehicleEscrow contract (TR L1 only for MVP).
     */
    fun getVehicleEscrow(credentials: Credentials): AriVehicleEscrowContract {
        return vehicleEscrowContract ?: run {
            val address = config.vehicleEscrowAddress
            require(address.isNotBlank()) { "Vehicle escrow address not configured" }
            val chainId = config.trL1ChainId
            AriVehicleEscrowContract(
                web3j = web3jProvider.getWeb3j(chainId),
                contractAddress = address,
                credentials = credentials,
                gasProvider = web3jProvider.getGasProvider(),
                chainId = chainId
            ).also { vehicleEscrowContract = it }
        }
    }

    /**
     * Get TokenHome contract for a specific currency.
     */
    fun getTokenHomeForCurrency(currency: String, credentials: Credentials): AriTokenHomeContract {
        val chainId = web3jProvider.getChainIdForCurrency(currency)
        return getTokenHome(chainId, credentials)
    }

    /**
     * Get TokenRemote contract for wrapped tokens of a specific currency.
     * Note: This returns the remote on the DESTINATION chain.
     * e.g., for "TRY" currency, returns the wTRY contract on EU L1.
     */
    fun getTokenRemoteForWrappedCurrency(currency: String, credentials: Credentials): AriTokenRemoteContract {
        // Wrapped tokens exist on the PARTNER chain
        val homeChainId = web3jProvider.getChainIdForCurrency(currency)
        val remoteChainId = config.getPartnerChainId(homeChainId)
        return getTokenRemote(remoteChainId, credentials)
    }
}

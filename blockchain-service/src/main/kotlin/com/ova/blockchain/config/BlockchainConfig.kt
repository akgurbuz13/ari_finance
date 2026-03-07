package com.ova.blockchain.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class BlockchainConfig(
    @Value("\${ari.region}") val region: String,
    @Value("\${ari.blockchain.tr-l1.rpc-url}") val trL1RpcUrl: String,
    @Value("\${ari.blockchain.tr-l1.chain-id}") val trL1ChainId: Long,
    @Value("\${ari.blockchain.tr-l1.stablecoin-address}") val trStablecoinAddress: String,
    @Value("\${ari.blockchain.eu-l1.rpc-url}") val euL1RpcUrl: String,
    @Value("\${ari.blockchain.eu-l1.chain-id}") val euL1ChainId: Long,
    @Value("\${ari.blockchain.eu-l1.stablecoin-address}") val euStablecoinAddress: String,
    // Cross-currency stablecoins (ariTRY on EU, ariEUR on TR)
    @Value("\${ari.blockchain.tr-l1.eur-stablecoin-address:}") val trEurStablecoinAddress: String,
    @Value("\${ari.blockchain.eu-l1.try-stablecoin-address:}") val euTryStablecoinAddress: String,
    // Burn-mint bridge addresses
    @Value("\${ari.blockchain.bridge.tr-burn-mint-bridge-address:}") val trBurnMintBridgeAddress: String,
    @Value("\${ari.blockchain.bridge.eu-burn-mint-bridge-address:}") val euBurnMintBridgeAddress: String,
    // ICTT bridge contracts
    @Value("\${ari.blockchain.bridge.tr-bridge-adapter-address:}") val trBridgeAdapterAddress: String,
    @Value("\${ari.blockchain.bridge.eu-bridge-adapter-address:}") val euBridgeAdapterAddress: String,
    @Value("\${ari.blockchain.bridge.tr-token-home-address:}") val trTokenHomeAddress: String,
    @Value("\${ari.blockchain.bridge.eu-token-home-address:}") val euTokenHomeAddress: String,
    @Value("\${ari.blockchain.bridge.tr-token-remote-address:}") val trTokenRemoteAddress: String,
    @Value("\${ari.blockchain.bridge.eu-token-remote-address:}") val euTokenRemoteAddress: String,
    @Value("\${ari.blockchain.bridge.tr-blockchain-id:}") val trBlockchainId: String,
    @Value("\${ari.blockchain.bridge.eu-blockchain-id:}") val euBlockchainId: String,
    // Vehicle escrow contracts (TR L1 only for MVP)
    @Value("\${ari.blockchain.vehicle.nft-address:}") val vehicleNftAddress: String,
    @Value("\${ari.blockchain.vehicle.escrow-address:}") val vehicleEscrowAddress: String,
    @Value("\${ari.blockchain.vehicle.treasury-address:}") val treasuryAddress: String,
    @Value("\${ari.blockchain.wallet.master-key}") val walletMasterKey: String,
    @Value("\${ari.core-banking.url}") val coreBankingUrl: String
) {
    fun getRpcUrl(): String = if (region == "TR") trL1RpcUrl else euL1RpcUrl
    fun getChainId(): Long = if (region == "TR") trL1ChainId else euL1ChainId
    fun getStablecoinAddress(): String = if (region == "TR") trStablecoinAddress else euStablecoinAddress

    /**
     * Get BridgeAdapter address for a chain.
     */
    fun getBridgeAdapterAddress(chainId: Long): String {
        return when (chainId) {
            trL1ChainId -> trBridgeAdapterAddress
            euL1ChainId -> euBridgeAdapterAddress
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    /**
     * Get TokenHome address for a chain.
     * TokenHome handles the native token on its home chain.
     */
    fun getTokenHomeAddress(chainId: Long): String {
        return when (chainId) {
            trL1ChainId -> trTokenHomeAddress
            euL1ChainId -> euTokenHomeAddress
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    /**
     * Get TokenRemote address for a chain.
     * TokenRemote handles wrapped tokens from partner chains.
     */
    fun getTokenRemoteAddress(chainId: Long): String {
        return when (chainId) {
            trL1ChainId -> trTokenRemoteAddress
            euL1ChainId -> euTokenRemoteAddress
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    /**
     * Get the Avalanche blockchain ID (bytes32 hex) for a chain.
     * Used for Teleporter cross-chain messaging.
     */
    fun getBlockchainId(chainId: Long): String {
        return when (chainId) {
            trL1ChainId -> trBlockchainId
            euL1ChainId -> euBlockchainId
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    /**
     * Get the partner chain ID for cross-chain transfers.
     */
    fun getPartnerChainId(chainId: Long): Long {
        return when (chainId) {
            trL1ChainId -> euL1ChainId
            euL1ChainId -> trL1ChainId
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    /**
     * Get stablecoin address for a specific currency on a specific chain.
     * Supports cross-currency (e.g. ariTRY on EU L1, ariEUR on TR L1).
     */
    fun getStablecoinAddress(chainId: Long, currency: String): String {
        return when {
            chainId == trL1ChainId && currency == "TRY" -> trStablecoinAddress
            chainId == trL1ChainId && currency == "EUR" -> trEurStablecoinAddress
            chainId == euL1ChainId && currency == "EUR" -> euStablecoinAddress
            chainId == euL1ChainId && currency == "TRY" -> euTryStablecoinAddress
            else -> throw IllegalArgumentException("No stablecoin for currency $currency on chain $chainId")
        }
    }

    /**
     * Get burn-mint bridge address for a chain.
     */
    fun getBurnMintBridgeAddress(chainId: Long): String {
        return when (chainId) {
            trL1ChainId -> trBurnMintBridgeAddress
            euL1ChainId -> euBurnMintBridgeAddress
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }

    /**
     * Get chain ID for a region.
     */
    fun getChainIdForRegion(region: String): Long {
        return when (region) {
            "TR" -> trL1ChainId
            "EU" -> euL1ChainId
            else -> throw IllegalArgumentException("Unknown region: $region")
        }
    }
}

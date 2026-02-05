package com.ova.blockchain.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class BlockchainConfig(
    @Value("\${ova.region}") val region: String,
    @Value("\${ova.blockchain.tr-l1.rpc-url}") val trL1RpcUrl: String,
    @Value("\${ova.blockchain.tr-l1.chain-id}") val trL1ChainId: Long,
    @Value("\${ova.blockchain.tr-l1.stablecoin-address}") val trStablecoinAddress: String,
    @Value("\${ova.blockchain.eu-l1.rpc-url}") val euL1RpcUrl: String,
    @Value("\${ova.blockchain.eu-l1.chain-id}") val euL1ChainId: Long,
    @Value("\${ova.blockchain.eu-l1.stablecoin-address}") val euStablecoinAddress: String,
    @Value("\${ova.blockchain.bridge.contract-address}") val bridgeContractAddress: String,
    @Value("\${ova.blockchain.bridge.tr-token-home-address:}") val trTokenHomeAddress: String,
    @Value("\${ova.blockchain.bridge.eu-token-home-address:}") val euTokenHomeAddress: String,
    @Value("\${ova.blockchain.bridge.tr-token-remote-address:}") val trTokenRemoteAddress: String,
    @Value("\${ova.blockchain.bridge.eu-token-remote-address:}") val euTokenRemoteAddress: String,
    @Value("\${ova.blockchain.wallet.master-key}") val walletMasterKey: String,
    @Value("\${ova.core-banking.url}") val coreBankingUrl: String
) {
    fun getRpcUrl(): String = if (region == "TR") trL1RpcUrl else euL1RpcUrl
    fun getChainId(): Long = if (region == "TR") trL1ChainId else euL1ChainId
    fun getStablecoinAddress(): String = if (region == "TR") trStablecoinAddress else euStablecoinAddress

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
     * Get the partner chain ID for cross-chain transfers.
     */
    fun getPartnerChainId(chainId: Long): Long {
        return when (chainId) {
            trL1ChainId -> euL1ChainId
            euL1ChainId -> trL1ChainId
            else -> throw IllegalArgumentException("Unknown chain ID: $chainId")
        }
    }
}

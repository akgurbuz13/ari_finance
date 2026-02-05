package com.ova.blockchain.wallet

import com.ova.blockchain.config.BlockchainConfig
import com.ova.blockchain.config.Web3jProvider
import com.ova.blockchain.repository.CustodialWalletRecord
import com.ova.blockchain.repository.WalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class CustodialWalletService(
    private val config: BlockchainConfig,
    private val web3jProvider: Web3jProvider,
    private val walletRepository: WalletRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class CustodialWallet(
        val userId: UUID,
        val address: String,
        val chainId: Long
    )

    fun getOrCreateWallet(userId: UUID, chainId: Long): CustodialWallet {
        val existing = walletRepository.findByUserAndChain(userId, chainId)
        if (existing != null) {
            return CustodialWallet(existing.userId, existing.address, existing.chainId)
        }

        val derivationIndex = walletRepository.getNextDerivationIndex()
        val credentials = deriveCredentials(userId, derivationIndex)
        val address = credentials.address

        val record = walletRepository.save(
            CustodialWalletRecord(
                userId = userId,
                chainId = chainId,
                address = "0x$address",
                derivationIndex = derivationIndex,
                encryptedKeyRef = "kms:derive:$derivationIndex"
            )
        )

        log.info("Created custodial wallet for user={} on chain={}: 0x{}", userId, chainId, address)
        return CustodialWallet(record.userId, record.address, record.chainId)
    }

    fun getOrCreateWalletForCurrency(userId: UUID, currency: String): CustodialWallet {
        val chainId = web3jProvider.getChainIdForCurrency(currency)
        return getOrCreateWallet(userId, chainId)
    }

    fun getWalletAddress(userId: UUID, chainId: Long): String? {
        return walletRepository.findByUserAndChain(userId, chainId)?.address
    }

    fun getCredentials(userId: UUID): Credentials {
        val wallet = walletRepository.findAllByUser(userId).firstOrNull()
        val derivationIndex = wallet?.derivationIndex ?: 0
        return deriveCredentials(userId, derivationIndex)
    }

    fun getMinterCredentials(): Credentials {
        // The minter/relayer key is the system-level key that has MINTER_ROLE on the stablecoin contract.
        // In production: fetched from KMS / Key Vault.
        return Credentials.create(config.walletMasterKey)
    }

    /**
     * Get credentials for the bridge operator.
     * Used for ICTT bridge operations (bridgeNativeTokens, bridgeWrappedTokensBack).
     * In production: should use a dedicated bridge-operator key from KMS / Key Vault.
     */
    fun getBridgeOperatorCredentials(): Credentials {
        // Uses the same master key as minter for now.
        // In production, this should use a dedicated bridge-operator key.
        return Credentials.create(config.walletMasterKey)
    }

    /**
     * Deterministic key derivation from master key + userId + index.
     * In production, this would be delegated to a KMS HSM.
     * This approach ensures the same userId always produces the same wallet address.
     */
    private fun deriveCredentials(userId: UUID, index: Int): Credentials {
        val derivationPath = "$userId:$index"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(config.walletMasterKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val seed = mac.doFinal(derivationPath.toByteArray(StandardCharsets.UTF_8))

        // Use first 32 bytes of HMAC output as private key
        val privateKeyHex = seed.joinToString("") { "%02x".format(it) }
        return Credentials.create(privateKeyHex)
    }
}

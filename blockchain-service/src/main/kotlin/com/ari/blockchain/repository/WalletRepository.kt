package com.ari.blockchain.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class CustodialWalletRecord(
    val id: Long? = null,
    val userId: UUID,
    val chainId: Long,
    val address: String,
    val derivationIndex: Int,
    val encryptedKeyRef: String? = null,
    val createdAt: Instant = Instant.now()
)

@Repository
class WalletRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        CustodialWalletRecord(
            id = rs.getLong("id"),
            userId = UUID.fromString(rs.getString("user_id")),
            chainId = rs.getLong("chain_id"),
            address = rs.getString("address"),
            derivationIndex = rs.getInt("derivation_index"),
            encryptedKeyRef = rs.getString("encrypted_key_ref"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(wallet: CustodialWalletRecord): CustodialWalletRecord {
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO blockchain.custodial_wallets
                (user_id, chain_id, address, derivation_index, encrypted_key_ref)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, chain_id) DO NOTHING
            RETURNING id
            """,
            Long::class.java,
            wallet.userId, wallet.chainId, wallet.address,
            wallet.derivationIndex, wallet.encryptedKeyRef
        )
        return if (id != null) wallet.copy(id = id) else findByUserAndChain(wallet.userId, wallet.chainId)!!
    }

    fun findByUserAndChain(userId: UUID, chainId: Long): CustodialWalletRecord? {
        return jdbcTemplate.query(
            "SELECT * FROM blockchain.custodial_wallets WHERE user_id = ? AND chain_id = ?",
            rowMapper, userId, chainId
        ).firstOrNull()
    }

    fun findByAddress(address: String): CustodialWalletRecord? {
        return jdbcTemplate.query(
            "SELECT * FROM blockchain.custodial_wallets WHERE address = ?",
            rowMapper, address
        ).firstOrNull()
    }

    fun findAllByUser(userId: UUID): List<CustodialWalletRecord> {
        return jdbcTemplate.query(
            "SELECT * FROM blockchain.custodial_wallets WHERE user_id = ? ORDER BY chain_id",
            rowMapper, userId
        )
    }

    fun getNextDerivationIndex(): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(derivation_index), -1) + 1 FROM blockchain.custodial_wallets",
            Int::class.java
        ) ?: 0
    }
}

package com.ari.platform.identity.internal.repository

import com.ari.platform.identity.internal.model.KycLevel
import com.ari.platform.identity.internal.model.KycStatus
import com.ari.platform.identity.internal.model.KycVerification
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class KycRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        KycVerification(
            id = UUID.fromString(rs.getString("id")),
            userId = UUID.fromString(rs.getString("user_id")),
            provider = rs.getString("provider"),
            providerRef = rs.getString("provider_ref"),
            status = KycStatus.fromValue(rs.getString("status")),
            level = KycLevel.fromValue(rs.getString("level")),
            decisionBy = rs.getString("decision_by")?.let { UUID.fromString(it) },
            decisionAt = rs.getTimestamp("decision_at")?.toInstant(),
            rejectionReason = rs.getString("rejection_reason"),
            expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(kyc: KycVerification): KycVerification {
        jdbcTemplate.update(
            """
            INSERT INTO identity.kyc_verifications (id, user_id, provider, provider_ref, status, level,
                decision_by, decision_at, rejection_reason, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            kyc.id, kyc.userId, kyc.provider, kyc.providerRef,
            kyc.status.value, kyc.level.value, kyc.decisionBy,
            kyc.decisionAt?.let { java.sql.Timestamp.from(it) },
            kyc.rejectionReason,
            kyc.expiresAt?.let { java.sql.Timestamp.from(it) }
        )
        return kyc
    }

    fun updateStatus(id: UUID, status: KycStatus, decisionBy: UUID?, rejectionReason: String?) {
        jdbcTemplate.update(
            """
            UPDATE identity.kyc_verifications
            SET status = ?, decision_by = ?, decision_at = now(), rejection_reason = ?
            WHERE id = ?
            """,
            status.value, decisionBy, rejectionReason, id
        )
    }

    fun findByUserId(userId: UUID): List<KycVerification> {
        return jdbcTemplate.query(
            "SELECT * FROM identity.kyc_verifications WHERE user_id = ? ORDER BY created_at DESC",
            rowMapper, userId
        )
    }

    fun findLatestByUserId(userId: UUID): KycVerification? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.kyc_verifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
            rowMapper, userId
        ).firstOrNull()
    }

    fun findById(id: UUID): KycVerification? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.kyc_verifications WHERE id = ?",
            rowMapper, id
        ).firstOrNull()
    }

    fun findByProviderRef(providerRef: String): KycVerification? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.kyc_verifications WHERE provider_ref = ? ORDER BY created_at DESC LIMIT 1",
            rowMapper, providerRef
        ).firstOrNull()
    }
}

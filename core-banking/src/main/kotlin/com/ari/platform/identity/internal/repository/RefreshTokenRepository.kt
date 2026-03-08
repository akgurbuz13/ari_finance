package com.ari.platform.identity.internal.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class RefreshTokenRepository(private val jdbcTemplate: JdbcTemplate) {

    fun save(userId: UUID, tokenHash: String, expiresAt: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO identity.refresh_tokens (user_id, token_hash, expires_at)
            VALUES (?, ?, ?)
            """,
            userId, tokenHash, Timestamp.from(expiresAt)
        )
    }

    fun findByTokenHash(tokenHash: String): RefreshTokenRecord? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.refresh_tokens WHERE token_hash = ? AND NOT revoked",
            { rs, _ ->
                RefreshTokenRecord(
                    id = UUID.fromString(rs.getString("id")),
                    userId = UUID.fromString(rs.getString("user_id")),
                    tokenHash = rs.getString("token_hash"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                    revoked = rs.getBoolean("revoked")
                )
            },
            tokenHash
        ).firstOrNull()
    }

    fun revokeByTokenHash(tokenHash: String) {
        jdbcTemplate.update(
            "UPDATE identity.refresh_tokens SET revoked = true WHERE token_hash = ?",
            tokenHash
        )
    }

    fun revokeAllByUserId(userId: UUID) {
        jdbcTemplate.update(
            "UPDATE identity.refresh_tokens SET revoked = true WHERE user_id = ?",
            userId
        )
    }
}

data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean
)

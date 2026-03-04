package com.ova.platform.identity.internal.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class PasswordResetTokenRepository(private val jdbcTemplate: JdbcTemplate) {

    fun save(userId: UUID, tokenHash: String, expiresAt: Instant) {
        // Invalidate any existing unused tokens for this user
        jdbcTemplate.update(
            "UPDATE identity.password_reset_tokens SET used = true WHERE user_id = ? AND NOT used",
            userId
        )
        jdbcTemplate.update(
            """
            INSERT INTO identity.password_reset_tokens (user_id, token_hash, expires_at)
            VALUES (?, ?, ?)
            """,
            userId, tokenHash, Timestamp.from(expiresAt)
        )
    }

    fun findValidByTokenHash(tokenHash: String): PasswordResetTokenRecord? {
        return jdbcTemplate.query(
            """
            SELECT * FROM identity.password_reset_tokens
            WHERE token_hash = ? AND NOT used AND expires_at > now()
            """,
            { rs, _ ->
                PasswordResetTokenRecord(
                    id = UUID.fromString(rs.getString("id")),
                    userId = UUID.fromString(rs.getString("user_id")),
                    tokenHash = rs.getString("token_hash"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                    used = rs.getBoolean("used")
                )
            },
            tokenHash
        ).firstOrNull()
    }

    fun markUsed(tokenHash: String) {
        jdbcTemplate.update(
            "UPDATE identity.password_reset_tokens SET used = true WHERE token_hash = ?",
            tokenHash
        )
    }
}

data class PasswordResetTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val used: Boolean
)

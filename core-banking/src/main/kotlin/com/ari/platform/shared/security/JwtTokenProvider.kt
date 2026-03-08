package com.ari.platform.shared.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/**
 * JWT token provider with JTI-based revocation support.
 *
 * SECURITY NOTES:
 * - The JWT secret MUST be at least 256 bits (32 bytes) for HS256
 * - In production, use RS256 with asymmetric keys for better security
 * - Revoked tokens are tracked in Redis for the duration of their validity
 */
@Component
class JwtTokenProvider(
    @Value("\${ari.jwt.secret}") private val secret: String,
    @Value("\${ari.jwt.access-token-expiry}") private val accessTokenExpiry: Long,
    @Value("\${ari.jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long,
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var key: SecretKey

    companion object {
        private const val MIN_SECRET_LENGTH = 32 // 256 bits
        private const val REVOKED_TOKEN_PREFIX = "jwt:revoked:"
        private const val USER_TOKENS_PREFIX = "jwt:user:"
    }

    @PostConstruct
    fun init() {
        // Validate secret is configured and meets minimum length
        if (secret.isBlank()) {
            throw IllegalStateException(
                "JWT secret is not configured. Set ARI_JWT_SECRET environment variable or ari.jwt.secret property."
            )
        }

        if (secret.length < MIN_SECRET_LENGTH) {
            throw IllegalStateException(
                "JWT secret must be at least $MIN_SECRET_LENGTH characters (256 bits). " +
                "Current length: ${secret.length}. Use a cryptographically secure random string."
            )
        }

        // Check for common insecure patterns
        val insecurePatterns = listOf("dev-secret", "test-secret", "change-in-production", "password", "secret123")
        if (insecurePatterns.any { secret.lowercase().contains(it) }) {
            log.warn(
                "JWT secret appears to contain an insecure pattern. " +
                "This is acceptable for development but MUST be changed in production."
            )
        }

        key = Keys.hmacShaKeyFor(secret.toByteArray())
        log.info("JWT provider initialized with {} bit key", secret.length * 8)
    }

    fun generateAccessToken(userId: UUID, email: String, roles: List<String> = emptyList()): String {
        val now = Date()
        val expiry = Date(now.time + accessTokenExpiry * 1000)
        val jti = UUID.randomUUID().toString()

        // Track this token for the user (enables revocation of all user tokens)
        trackUserToken(userId, jti, accessTokenExpiry)

        return Jwts.builder()
            .id(jti)  // JTI claim for revocation
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(userId: UUID): String {
        val now = Date()
        val expiry = Date(now.time + refreshTokenExpiry * 1000)
        val jti = UUID.randomUUID().toString()

        // Track this token for the user
        trackUserToken(userId, jti, refreshTokenExpiry)

        return Jwts.builder()
            .id(jti)  // JTI claim for revocation
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            // Check if token has been revoked
            val jti = claims.id
            if (jti != null && isTokenRevoked(jti)) {
                log.debug("Token {} has been revoked", jti)
                return null
            }

            claims
        } catch (e: Exception) {
            log.debug("Token validation failed: {}", e.message)
            null
        }
    }

    fun getUserId(token: String): UUID? {
        return validateToken(token)?.subject?.let { UUID.fromString(it) }
    }

    fun getTokenType(token: String): String? {
        return validateToken(token)?.get("type", String::class.java)
    }

    /**
     * Revoke a specific token by its JTI.
     */
    fun revokeToken(token: String) {
        val claims = try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            log.warn("Cannot revoke invalid token")
            return
        }

        val jti = claims.id ?: return
        val expiry = claims.expiration

        // Store revocation until token would have expired anyway
        val ttl = (expiry.time - System.currentTimeMillis()).coerceAtLeast(0)
        if (ttl > 0) {
            redisTemplate.opsForValue().set(
                "$REVOKED_TOKEN_PREFIX$jti",
                "revoked",
                ttl,
                TimeUnit.MILLISECONDS
            )
            log.debug("Revoked token {}", jti)
        }
    }

    /**
     * Revoke all tokens for a user (e.g., on password change, account compromise).
     */
    fun revokeAllUserTokens(userId: UUID) {
        val pattern = "$USER_TOKENS_PREFIX$userId:*"
        val keys = redisTemplate.keys(pattern)
        if (keys.isNotEmpty()) {
            // Mark all user tokens as revoked
            keys.forEach { key ->
                val jti = key.substringAfterLast(":")
                val ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)
                if (ttl > 0) {
                    redisTemplate.opsForValue().set(
                        "$REVOKED_TOKEN_PREFIX$jti",
                        "revoked",
                        ttl,
                        TimeUnit.MILLISECONDS
                    )
                }
            }
            redisTemplate.delete(keys)
            log.info("Revoked {} tokens for user {}", keys.size, userId)
        }
    }

    private fun isTokenRevoked(jti: String): Boolean {
        return redisTemplate.hasKey("$REVOKED_TOKEN_PREFIX$jti")
    }

    private fun trackUserToken(userId: UUID, jti: String, expirySeconds: Long) {
        redisTemplate.opsForValue().set(
            "$USER_TOKENS_PREFIX$userId:$jti",
            "active",
            expirySeconds,
            TimeUnit.SECONDS
        )
    }
}

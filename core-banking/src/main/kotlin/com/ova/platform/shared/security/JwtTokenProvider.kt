package com.ova.platform.shared.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${ova.jwt.secret}") private val secret: String,
    @Value("\${ova.jwt.access-token-expiry}") private val accessTokenExpiry: Long,
    @Value("\${ova.jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateAccessToken(userId: UUID, email: String, roles: List<String> = emptyList()): String {
        val now = Date()
        val expiry = Date(now.time + accessTokenExpiry * 1000)

        return Jwts.builder()
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

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            null
        }
    }

    fun getUserId(token: String): UUID? {
        return validateToken(token)?.subject?.let { UUID.fromString(it) }
    }

    fun getTokenType(token: String): String? {
        return validateToken(token)?.get("type", String::class.java)
    }
}

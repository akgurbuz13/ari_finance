package com.ari.platform.identity.internal.service

import com.ari.platform.identity.event.UserCreated
import com.ari.platform.identity.internal.model.User
import com.ari.platform.identity.internal.model.UserStatus
import com.ari.platform.identity.internal.repository.PasswordResetTokenRepository
import com.ari.platform.identity.internal.repository.RefreshTokenRepository
import com.ari.platform.identity.internal.repository.UserRepository
import com.ari.platform.shared.event.OutboxPublisher
import com.ari.platform.shared.exception.BadRequestException
import com.ari.platform.shared.exception.ConflictException
import com.ari.platform.shared.exception.UnauthorizedException
import com.ari.platform.shared.security.AuditService
import com.ari.platform.shared.security.JwtTokenProvider
import dev.samstevens.totp.code.CodeVerifier
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val outboxPublisher: OutboxPublisher,
    private val auditService: AuditService
) {
    private val secretGenerator = DefaultSecretGenerator()
    private val codeVerifier: CodeVerifier = DefaultCodeVerifier(
        DefaultCodeGenerator(), SystemTimeProvider()
    )

    @Transactional
    fun signup(email: String, phone: String, password: String, region: String): AuthTokens {
        if (userRepository.existsByEmail(email)) {
            throw ConflictException("Email already registered")
        }
        if (userRepository.existsByPhone(phone)) {
            throw ConflictException("Phone number already registered")
        }

        val user = userRepository.save(
            User(
                email = email,
                phone = phone,
                passwordHash = passwordEncoder.encode(password),
                region = region
            )
        )

        outboxPublisher.publish(UserCreated(user.id, user.email, user.region))
        auditService.log(user.id, "user", "signup", "user", user.id.toString())

        return generateTokens(user)
    }

    @Transactional
    fun login(email: String, password: String, totpCode: String? = null): AuthTokens {
        val user = userRepository.findByEmail(email)
            ?: throw UnauthorizedException("Invalid credentials")

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw UnauthorizedException("Invalid credentials")
        }

        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.CLOSED) {
            throw UnauthorizedException("Account is ${user.status.value}")
        }

        if (user.totpEnabled) {
            if (totpCode == null) {
                throw BadRequestException("2FA code required")
            }
            if (!codeVerifier.isValidCode(user.totpSecret, totpCode)) {
                throw UnauthorizedException("Invalid 2FA code")
            }
        }

        auditService.log(user.id, "user", "login", "user", user.id.toString())

        return generateTokens(user)
    }

    @Transactional
    fun refreshToken(refreshToken: String): AuthTokens {
        val claims = jwtTokenProvider.validateToken(refreshToken)
            ?: throw UnauthorizedException("Invalid refresh token")

        if (claims.get("type", String::class.java) != "refresh") {
            throw UnauthorizedException("Invalid token type")
        }

        val tokenHash = hashToken(refreshToken)
        val storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw UnauthorizedException("Refresh token not found or revoked")

        if (storedToken.expiresAt.isBefore(Instant.now())) {
            throw UnauthorizedException("Refresh token expired")
        }

        // Revoke old token
        refreshTokenRepository.revokeByTokenHash(tokenHash)

        val userId = UUID.fromString(claims.subject)
        val user = userRepository.findById(userId)
            ?: throw UnauthorizedException("User not found")

        return generateTokens(user)
    }

    @Transactional
    fun logout(userId: UUID) {
        refreshTokenRepository.revokeAllByUserId(userId)
        auditService.log(userId, "user", "logout", "user", userId.toString())
    }

    fun setupTotp(userId: UUID): TotpSetup {
        val user = userRepository.findById(userId)
            ?: throw UnauthorizedException("User not found")

        val secret = secretGenerator.generate()
        userRepository.update(user.copy(totpSecret = secret))

        return TotpSetup(
            secret = secret,
            uri = "otpauth://totp/ARI:${user.email}?secret=$secret&issuer=ARI"
        )
    }

    @Transactional
    fun enableTotp(userId: UUID, code: String): Boolean {
        val user = userRepository.findById(userId)
            ?: throw UnauthorizedException("User not found")

        if (user.totpSecret == null) {
            throw BadRequestException("TOTP not set up. Call setup first.")
        }

        if (!codeVerifier.isValidCode(user.totpSecret, code)) {
            throw BadRequestException("Invalid TOTP code")
        }

        userRepository.update(user.copy(totpEnabled = true))
        auditService.log(userId, "user", "enable_2fa", "user", userId.toString())
        return true
    }

    @Transactional
    fun requestPasswordReset(email: String): String? {
        val user = userRepository.findByEmail(email) ?: return null

        val resetToken = UUID.randomUUID().toString()
        val tokenHash = hashToken(resetToken)
        val expiresAt = Instant.now().plusSeconds(3600) // 1 hour

        passwordResetTokenRepository.save(user.id, tokenHash, expiresAt)

        auditService.log(user.id, "user", "password_reset_requested", "user", user.id.toString())

        // MVP: return token directly (no email service)
        // Production: send token via email, return null
        return resetToken
    }

    @Transactional
    fun resetPassword(token: String, newPassword: String) {
        val tokenHash = hashToken(token)
        val resetToken = passwordResetTokenRepository.findValidByTokenHash(tokenHash)
            ?: throw BadRequestException("Invalid or expired reset token")

        val user = userRepository.findById(resetToken.userId)
            ?: throw BadRequestException("User not found")

        userRepository.update(user.copy(passwordHash = passwordEncoder.encode(newPassword)))
        passwordResetTokenRepository.markUsed(tokenHash)

        // Revoke all refresh tokens for security
        refreshTokenRepository.revokeAllByUserId(user.id)

        auditService.log(user.id, "user", "password_reset_completed", "user", user.id.toString())
    }

    private fun generateTokens(user: User): AuthTokens {
        val accessToken = jwtTokenProvider.generateAccessToken(user.id, user.email, roles = listOf(user.role))
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        val tokenHash = hashToken(refreshToken)
        val expiresAt = Instant.now().plusSeconds(604800) // 7 days
        refreshTokenRepository.save(user.id, tokenHash, expiresAt)

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID
)

data class TotpSetup(
    val secret: String,
    val uri: String
)

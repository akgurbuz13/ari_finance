package com.ova.platform.identity.api

import com.ova.platform.identity.internal.service.AuthService
import com.ova.platform.identity.internal.service.AuthTokens
import com.ova.platform.identity.internal.service.TotpSetup
import com.ova.platform.shared.security.AuditService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val auditService: AuditService
) {

    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val clientIp = extractClientIp(httpRequest)
        val tokens = authService.signup(request.email, request.phone, request.password, request.region)

        auditService.log(
            actorId = tokens.userId,
            actorType = "USER",
            action = "SIGNUP",
            resourceType = "USER",
            resourceId = tokens.userId.toString(),
            details = mapOf("email" to request.email, "region" to request.region),
            ipAddress = clientIp
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(tokens))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val clientIp = extractClientIp(httpRequest)
        val tokens = authService.login(request.email, request.password, request.totpCode)

        auditService.log(
            actorId = tokens.userId,
            actorType = "USER",
            action = "LOGIN",
            resourceType = "SESSION",
            resourceId = tokens.userId.toString(),
            details = mapOf("email" to request.email, "has_totp" to (request.totpCode != null)),
            ipAddress = clientIp
        )

        return ResponseEntity.ok(AuthResponse.from(tokens))
    }

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val clientIp = extractClientIp(httpRequest)
        val tokens = authService.refreshToken(request.refreshToken)

        auditService.log(
            actorId = tokens.userId,
            actorType = "USER",
            action = "TOKEN_REFRESH",
            resourceType = "SESSION",
            resourceId = tokens.userId.toString(),
            ipAddress = clientIp
        )

        return ResponseEntity.ok(AuthResponse.from(tokens))
    }

    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ResponseEntity<Void> {
        val clientIp = extractClientIp(httpRequest)
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        authService.logout(userId)

        auditService.log(
            actorId = userId,
            actorType = "USER",
            action = "LOGOUT",
            resourceType = "SESSION",
            resourceId = userId.toString(),
            ipAddress = clientIp
        )

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/2fa/setup")
    fun setupTotp(httpRequest: HttpServletRequest): ResponseEntity<TotpSetupResponse> {
        val clientIp = extractClientIp(httpRequest)
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val setup = authService.setupTotp(userId)

        auditService.log(
            actorId = userId,
            actorType = "USER",
            action = "TOTP_SETUP_INITIATED",
            resourceType = "USER",
            resourceId = userId.toString(),
            ipAddress = clientIp
        )

        return ResponseEntity.ok(TotpSetupResponse(setup.secret, setup.uri))
    }

    @PostMapping("/2fa/enable")
    fun enableTotp(
        @Valid @RequestBody request: TotpEnableRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Boolean>> {
        val clientIp = extractClientIp(httpRequest)
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val enabled = authService.enableTotp(userId, request.code)

        auditService.log(
            actorId = userId,
            actorType = "USER",
            action = if (enabled) "TOTP_ENABLED" else "TOTP_ENABLE_FAILED",
            resourceType = "USER",
            resourceId = userId.toString(),
            ipAddress = clientIp
        )

        return ResponseEntity.ok(mapOf("enabled" to enabled))
    }

    /**
     * Extract client IP address, considering proxy headers.
     * Order of preference: X-Forwarded-For, X-Real-IP, remoteAddr
     */
    private fun extractClientIp(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            return forwardedFor.split(",").first().trim()
        }

        val realIp = request.getHeader("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp.trim()
        }

        return request.remoteAddr ?: "unknown"
    }
}

data class SignupRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val phone: String,
    @field:Size(min = 8) @field:NotBlank val password: String,
    @field:Pattern(regexp = "TR|EU") val region: String
)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    val totpCode: String? = null
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)

data class TotpEnableRequest(
    @field:NotBlank val code: String
)

data class TotpSetupResponse(
    val secret: String,
    val uri: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
) {
    companion object {
        fun from(tokens: AuthTokens) = AuthResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            userId = tokens.userId.toString()
        )
    }
}

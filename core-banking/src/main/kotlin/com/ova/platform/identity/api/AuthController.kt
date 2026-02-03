package com.ova.platform.identity.api

import com.ova.platform.identity.internal.service.AuthService
import com.ova.platform.identity.internal.service.AuthTokens
import com.ova.platform.identity.internal.service.TotpSetup
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
class AuthController(private val authService: AuthService) {

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<AuthResponse> {
        val tokens = authService.signup(request.email, request.phone, request.password, request.region)
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(tokens))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val tokens = authService.login(request.email, request.password, request.totpCode)
        return ResponseEntity.ok(AuthResponse.from(tokens))
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val tokens = authService.refreshToken(request.refreshToken)
        return ResponseEntity.ok(AuthResponse.from(tokens))
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        authService.logout(userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/2fa/setup")
    fun setupTotp(): ResponseEntity<TotpSetupResponse> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val setup = authService.setupTotp(userId)
        return ResponseEntity.ok(TotpSetupResponse(setup.secret, setup.uri))
    }

    @PostMapping("/2fa/enable")
    fun enableTotp(@Valid @RequestBody request: TotpEnableRequest): ResponseEntity<Map<String, Boolean>> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val enabled = authService.enableTotp(userId, request.code)
        return ResponseEntity.ok(mapOf("enabled" to enabled))
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

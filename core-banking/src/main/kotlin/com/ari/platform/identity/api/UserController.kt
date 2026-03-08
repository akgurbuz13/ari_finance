package com.ari.platform.identity.api

import com.ari.platform.identity.internal.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun getProfile(): ResponseEntity<UserProfileResponse> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val user = userService.getUser(userId)
        return ResponseEntity.ok(
            UserProfileResponse(
                id = user.id.toString(),
                email = user.email,
                phone = user.phone,
                firstName = user.firstName,
                lastName = user.lastName,
                dateOfBirth = user.dateOfBirth?.toString(),
                nationality = user.nationality,
                status = user.status.value,
                region = user.region,
                totpEnabled = user.totpEnabled,
                createdAt = user.createdAt.toString()
            )
        )
    }

    @PatchMapping("/me")
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ResponseEntity<UserProfileResponse> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val user = userService.updateProfile(
            userId = userId,
            firstName = request.firstName,
            lastName = request.lastName,
            dateOfBirth = request.dateOfBirth?.let { LocalDate.parse(it) },
            nationality = request.nationality
        )
        return ResponseEntity.ok(
            UserProfileResponse(
                id = user.id.toString(),
                email = user.email,
                phone = user.phone,
                firstName = user.firstName,
                lastName = user.lastName,
                dateOfBirth = user.dateOfBirth?.toString(),
                nationality = user.nationality,
                status = user.status.value,
                region = user.region,
                totpEnabled = user.totpEnabled,
                createdAt = user.createdAt.toString()
            )
        )
    }
}

data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: String? = null,
    val nationality: String? = null
)

data class UserProfileResponse(
    val id: String,
    val email: String,
    val phone: String,
    val firstName: String?,
    val lastName: String?,
    val dateOfBirth: String?,
    val nationality: String?,
    val status: String,
    val region: String,
    val totpEnabled: Boolean,
    val createdAt: String
)

package com.ova.platform.identity.api

import com.ova.platform.identity.internal.model.KycLevel
import com.ova.platform.identity.internal.service.KycService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/kyc")
class KycController(private val kycService: KycService) {

    @PostMapping("/initiate")
    fun initiateKyc(@Valid @RequestBody request: InitiateKycRequest): ResponseEntity<KycResponse> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val kyc = kycService.initiateKyc(
            userId = userId,
            provider = request.provider,
            providerRef = request.providerRef,
            level = KycLevel.fromValue(request.level)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            KycResponse(
                id = kyc.id.toString(),
                status = kyc.status.value,
                level = kyc.level.value,
                provider = kyc.provider,
                createdAt = kyc.createdAt.toString()
            )
        )
    }

    @GetMapping("/status")
    fun getKycStatus(): ResponseEntity<KycResponse?> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val kyc = kycService.getKycStatus(userId)
        return if (kyc != null) {
            ResponseEntity.ok(
                KycResponse(
                    id = kyc.id.toString(),
                    status = kyc.status.value,
                    level = kyc.level.value,
                    provider = kyc.provider,
                    createdAt = kyc.createdAt.toString()
                )
            )
        } else {
            ResponseEntity.noContent().build()
        }
    }

    @GetMapping("/history")
    fun getKycHistory(): ResponseEntity<List<KycResponse>> {
        val userId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val history = kycService.getKycHistory(userId).map {
            KycResponse(
                id = it.id.toString(),
                status = it.status.value,
                level = it.level.value,
                provider = it.provider,
                createdAt = it.createdAt.toString()
            )
        }
        return ResponseEntity.ok(history)
    }

    // Admin endpoints
    @PostMapping("/{kycId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    fun approveKyc(@PathVariable kycId: UUID): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        kycService.approveKyc(kycId, adminId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{kycId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    fun rejectKyc(
        @PathVariable kycId: UUID,
        @Valid @RequestBody request: RejectKycRequest
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        kycService.rejectKyc(kycId, adminId, request.reason)
        return ResponseEntity.ok().build()
    }
}

data class InitiateKycRequest(
    @field:NotBlank val provider: String,
    @field:NotBlank val providerRef: String,
    val level: String = "basic"
)

data class RejectKycRequest(
    @field:NotBlank val reason: String
)

data class KycResponse(
    val id: String,
    val status: String,
    val level: String,
    val provider: String,
    val createdAt: String
)

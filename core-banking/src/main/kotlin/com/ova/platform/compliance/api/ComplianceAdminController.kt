package com.ova.platform.compliance.api

import com.ova.platform.compliance.internal.service.CaseManagementService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/compliance")
@PreAuthorize("hasRole('ADMIN')")
class ComplianceAdminController(
    private val caseManagementService: CaseManagementService
) {

    @PostMapping("/cases/{caseId}/resolve")
    fun resolveCase(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: ResolveCaseRequest
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        caseManagementService.resolveCase(caseId, adminId, request.resolution)
        return ResponseEntity.ok().build()
    }
}

data class ResolveCaseRequest(
    @field:NotBlank val resolution: String
)

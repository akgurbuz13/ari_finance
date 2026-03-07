package com.ova.platform.payments.api

import com.ova.platform.payments.internal.model.VehicleEscrow
import com.ova.platform.payments.internal.model.VehicleRegistration
import com.ova.platform.payments.internal.service.VehicleEscrowService
import com.ova.platform.payments.internal.service.VehicleRegistrationService
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController(
    private val vehicleRegistrationService: VehicleRegistrationService,
    private val vehicleEscrowService: VehicleEscrowService
) {

    @PostMapping
    fun registerVehicle(
        @Valid @RequestBody request: RegisterVehicleRequest
    ): ResponseEntity<VehicleResponse> {
        val userId = currentUserId()
        val vehicle = vehicleRegistrationService.registerVehicle(
            userId = userId,
            vin = request.vin,
            plateNumber = request.plateNumber,
            make = request.make,
            model = request.model,
            year = request.year,
            color = request.color,
            mileage = request.mileage,
            fuelType = request.fuelType,
            transmission = request.transmission
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicle.toResponse())
    }

    @GetMapping
    fun getMyVehicles(): ResponseEntity<List<VehicleResponse>> {
        val userId = currentUserId()
        val vehicles = vehicleRegistrationService.getMyVehicles(userId)
        return ResponseEntity.ok(vehicles.map { it.toResponse() })
    }

    @GetMapping("/{id}")
    fun getVehicle(@PathVariable id: UUID): ResponseEntity<VehicleResponse> {
        val userId = currentUserId()
        val vehicle = vehicleRegistrationService.getVehicle(userId, id)
        return ResponseEntity.ok(vehicle.toResponse())
    }

    @PostMapping("/escrow")
    fun createEscrow(
        @Valid @RequestBody request: CreateEscrowRequest
    ): ResponseEntity<EscrowResponse> {
        val userId = currentUserId()
        val escrow = vehicleEscrowService.createEscrow(
            sellerUserId = userId,
            vehicleRegistrationId = request.vehicleRegistrationId,
            saleAmount = request.saleAmount
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(escrow.toResponse())
    }

    @GetMapping("/escrow")
    fun getMyEscrows(): ResponseEntity<List<EscrowResponse>> {
        val userId = currentUserId()
        val escrows = vehicleEscrowService.getMyEscrows(userId)
        return ResponseEntity.ok(escrows.map { it.toResponse() })
    }

    @GetMapping("/escrow/{id}")
    fun getEscrow(@PathVariable id: UUID): ResponseEntity<EscrowResponse> {
        val userId = currentUserId()
        val escrow = vehicleEscrowService.getEscrow(userId, id)
        return ResponseEntity.ok(escrow.toResponse())
    }

    @GetMapping("/escrow/code/{shareCode}")
    fun getEscrowByShareCode(@PathVariable shareCode: String): ResponseEntity<EscrowResponse> {
        val escrow = vehicleEscrowService.getEscrowByShareCode(shareCode)
        return ResponseEntity.ok(escrow.toResponse())
    }

    @PostMapping("/escrow/join/{shareCode}")
    fun joinEscrow(@PathVariable shareCode: String): ResponseEntity<EscrowResponse> {
        val userId = currentUserId()
        val escrow = vehicleEscrowService.joinEscrow(userId, shareCode)
        return ResponseEntity.ok(escrow.toResponse())
    }

    @PostMapping("/escrow/{id}/fund")
    fun fundEscrow(@PathVariable id: UUID): ResponseEntity<EscrowResponse> {
        val userId = currentUserId()
        val escrow = vehicleEscrowService.fundEscrow(userId, id)
        return ResponseEntity.ok(escrow.toResponse())
    }

    @PostMapping("/escrow/{id}/confirm")
    fun confirmEscrow(@PathVariable id: UUID): ResponseEntity<EscrowResponse> {
        val userId = currentUserId()
        val escrow = vehicleEscrowService.confirmEscrow(userId, id)
        return ResponseEntity.ok(escrow.toResponse())
    }

    @PostMapping("/escrow/{id}/cancel")
    fun cancelEscrow(@PathVariable id: UUID): ResponseEntity<EscrowResponse> {
        val userId = currentUserId()
        val escrow = vehicleEscrowService.cancelEscrow(userId, id)
        return ResponseEntity.ok(escrow.toResponse())
    }

    private fun currentUserId(): UUID {
        return UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
    }

    private fun VehicleRegistration.toResponse() = VehicleResponse(
        id = id.toString(),
        tokenId = tokenId,
        ownerUserId = ownerUserId.toString(),
        vin = vin,
        plateNumber = plateNumber,
        make = make,
        model = model,
        year = year,
        color = color,
        mileage = mileage,
        fuelType = fuelType,
        transmission = transmission,
        chainId = chainId,
        mintTxHash = mintTxHash,
        status = status.value,
        createdAt = createdAt.toString()
    )

    private fun VehicleEscrow.toResponse() = EscrowResponse(
        id = id.toString(),
        onChainEscrowId = onChainEscrowId,
        vehicleRegistrationId = vehicleRegistrationId.toString(),
        sellerUserId = sellerUserId.toString(),
        buyerUserId = buyerUserId?.toString(),
        saleAmount = saleAmount.toPlainString(),
        feeAmount = feeAmount.toPlainString(),
        currency = currency,
        state = state.value,
        sellerConfirmed = sellerConfirmed,
        buyerConfirmed = buyerConfirmed,
        shareCode = shareCode,
        setupTxHash = setupTxHash,
        fundTxHash = fundTxHash,
        completeTxHash = completeTxHash,
        createdAt = createdAt.toString(),
        completedAt = completedAt?.toString()
    )
}

// Request DTOs
data class RegisterVehicleRequest(
    @field:NotBlank @field:Size(min = 17, max = 17, message = "VIN must be 17 characters")
    val vin: String,
    @field:NotBlank
    val plateNumber: String,
    @field:NotBlank
    val make: String,
    @field:NotBlank
    val model: String,
    @field:NotNull @field:Min(1900)
    val year: Int,
    val color: String? = null,
    val mileage: Int? = null,
    val fuelType: String? = null,
    val transmission: String? = null
)

data class CreateEscrowRequest(
    @field:NotNull
    val vehicleRegistrationId: UUID,
    @field:NotNull @field:DecimalMin("0.01")
    val saleAmount: BigDecimal
)

// Response DTOs
data class VehicleResponse(
    val id: String,
    val tokenId: Long?,
    val ownerUserId: String,
    val vin: String,
    val plateNumber: String,
    val make: String,
    val model: String,
    val year: Int,
    val color: String?,
    val mileage: Int?,
    val fuelType: String?,
    val transmission: String?,
    val chainId: Long,
    val mintTxHash: String?,
    val status: String,
    val createdAt: String
)

data class EscrowResponse(
    val id: String,
    val onChainEscrowId: Long?,
    val vehicleRegistrationId: String,
    val sellerUserId: String,
    val buyerUserId: String?,
    val saleAmount: String,
    val feeAmount: String,
    val currency: String,
    val state: String,
    val sellerConfirmed: Boolean,
    val buyerConfirmed: Boolean,
    val shareCode: String,
    val setupTxHash: String?,
    val fundTxHash: String?,
    val completeTxHash: String?,
    val createdAt: String,
    val completedAt: String?
)

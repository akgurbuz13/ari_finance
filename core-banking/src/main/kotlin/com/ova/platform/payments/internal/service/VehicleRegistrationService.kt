package com.ova.platform.payments.internal.service

import com.ova.platform.payments.event.VehicleMintRequested
import com.ova.platform.payments.internal.model.VehicleRegistration
import com.ova.platform.payments.internal.model.VehicleStatus
import com.ova.platform.payments.internal.repository.VehicleRegistrationRepository
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.ConflictException
import com.ova.platform.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.util.UUID

@Service
class VehicleRegistrationService(
    private val vehicleRegistrationRepository: VehicleRegistrationRepository,
    private val outboxPublisher: OutboxPublisher,
    @Value("\${ari.blockchain.tr-l1.chain-id:99999}") private val trChainId: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registerVehicle(
        userId: UUID,
        vin: String,
        plateNumber: String,
        make: String,
        model: String,
        year: Int,
        color: String? = null,
        mileage: Int? = null,
        fuelType: String? = null,
        transmission: String? = null
    ): VehicleRegistration {
        val vinHash = keccak256Hex(vin)
        val plateHash = keccak256Hex(plateNumber)

        // Check uniqueness
        vehicleRegistrationRepository.findByVinHash(vinHash)?.let {
            throw ConflictException("Vehicle with this VIN is already registered")
        }
        vehicleRegistrationRepository.findByPlateNumber(plateNumber)?.let {
            throw ConflictException("Vehicle with this plate number is already registered")
        }

        val metadataUri = "https://api.arifinance.co/api/v1/vehicles/metadata/$vinHash"

        val registration = vehicleRegistrationRepository.save(
            VehicleRegistration(
                ownerUserId = userId,
                vin = vin,
                vinHash = vinHash,
                plateNumber = plateNumber,
                plateHash = plateHash,
                make = make,
                model = model,
                year = year,
                color = color,
                mileage = mileage,
                fuelType = fuelType,
                transmission = transmission,
                metadataUri = metadataUri,
                chainId = trChainId
            )
        )

        outboxPublisher.publish(
            VehicleMintRequested(
                vehicleRegistrationId = registration.id,
                ownerUserId = userId,
                vinHash = vinHash,
                plateHash = plateHash,
                metadataUri = metadataUri
            )
        )

        log.info("Vehicle registered: id={}, vin={}..., plate={}", registration.id, vin.take(6), plateNumber)
        return registration
    }

    fun getMyVehicles(userId: UUID): List<VehicleRegistration> {
        return vehicleRegistrationRepository.findByOwnerUserId(userId)
    }

    fun getVehicle(userId: UUID, vehicleId: UUID): VehicleRegistration {
        val vehicle = vehicleRegistrationRepository.findById(vehicleId)
            ?: throw NotFoundException("Vehicle", vehicleId.toString())
        if (vehicle.ownerUserId != userId) {
            throw BadRequestException("Vehicle does not belong to this user")
        }
        return vehicle
    }

    fun getVehicleById(vehicleId: UUID): VehicleRegistration {
        return vehicleRegistrationRepository.findById(vehicleId)
            ?: throw NotFoundException("Vehicle", vehicleId.toString())
    }

    private fun keccak256Hex(input: String): String {
        val digest = Keccak.Digest256()
        val hash = digest.digest(input.toByteArray())
        return "0x" + hash.joinToString("") { "%02x".format(it) }
    }
}

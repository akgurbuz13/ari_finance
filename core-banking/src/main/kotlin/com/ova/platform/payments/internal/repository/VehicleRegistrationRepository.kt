package com.ova.platform.payments.internal.repository

import com.ova.platform.payments.internal.model.VehicleRegistration
import com.ova.platform.payments.internal.model.VehicleStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class VehicleRegistrationRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        VehicleRegistration(
            id = UUID.fromString(rs.getString("id")),
            tokenId = rs.getLong("token_id").takeIf { !rs.wasNull() },
            ownerUserId = UUID.fromString(rs.getString("owner_user_id")),
            vin = rs.getString("vin"),
            vinHash = rs.getString("vin_hash"),
            plateNumber = rs.getString("plate_number"),
            plateHash = rs.getString("plate_hash"),
            make = rs.getString("make"),
            model = rs.getString("model"),
            year = rs.getInt("year"),
            color = rs.getString("color"),
            mileage = rs.getInt("mileage").takeIf { !rs.wasNull() },
            fuelType = rs.getString("fuel_type"),
            transmission = rs.getString("transmission"),
            metadataUri = rs.getString("metadata_uri"),
            chainId = rs.getLong("chain_id"),
            mintTxHash = rs.getString("mint_tx_hash"),
            status = VehicleStatus.fromValue(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun save(registration: VehicleRegistration): VehicleRegistration {
        jdbcTemplate.update(
            """
            INSERT INTO payments.vehicle_registrations
                (id, token_id, owner_user_id, vin, vin_hash, plate_number, plate_hash,
                 make, model, year, color, mileage, fuel_type, transmission,
                 metadata_uri, chain_id, mint_tx_hash, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            registration.id, registration.tokenId, registration.ownerUserId,
            registration.vin, registration.vinHash, registration.plateNumber, registration.plateHash,
            registration.make, registration.model, registration.year,
            registration.color, registration.mileage, registration.fuelType, registration.transmission,
            registration.metadataUri, registration.chainId, registration.mintTxHash, registration.status.value
        )
        return registration
    }

    fun findById(id: UUID): VehicleRegistration? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_registrations WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun findByOwnerUserId(userId: UUID): List<VehicleRegistration> {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_registrations WHERE owner_user_id = ? ORDER BY created_at DESC",
            rowMapper, userId
        )
    }

    fun findByVinHash(vinHash: String): VehicleRegistration? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_registrations WHERE vin_hash = ?", rowMapper, vinHash
        ).firstOrNull()
    }

    fun findByPlateNumber(plateNumber: String): VehicleRegistration? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.vehicle_registrations WHERE plate_number = ?", rowMapper, plateNumber
        ).firstOrNull()
    }

    fun updateStatus(id: UUID, status: VehicleStatus) {
        jdbcTemplate.update(
            "UPDATE payments.vehicle_registrations SET status = ?, updated_at = now() WHERE id = ?",
            status.value, id
        )
    }

    fun updateTokenIdAndTxHash(id: UUID, tokenId: Long, txHash: String) {
        jdbcTemplate.update(
            """
            UPDATE payments.vehicle_registrations
            SET token_id = ?, mint_tx_hash = ?, status = ?, updated_at = now()
            WHERE id = ?
            """,
            tokenId, txHash, VehicleStatus.MINTED.value, id
        )
    }

    fun updateOwner(id: UUID, newOwnerUserId: UUID) {
        jdbcTemplate.update(
            "UPDATE payments.vehicle_registrations SET owner_user_id = ?, updated_at = now() WHERE id = ?",
            newOwnerUserId, id
        )
    }
}

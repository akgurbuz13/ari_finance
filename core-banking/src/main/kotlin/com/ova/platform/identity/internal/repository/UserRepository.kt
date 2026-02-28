package com.ova.platform.identity.internal.repository

import com.ova.platform.identity.internal.model.User
import com.ova.platform.identity.internal.model.UserStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class UserRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        User(
            id = UUID.fromString(rs.getString("id")),
            email = rs.getString("email"),
            phone = rs.getString("phone"),
            passwordHash = rs.getString("password_hash"),
            firstName = rs.getString("first_name"),
            lastName = rs.getString("last_name"),
            dateOfBirth = rs.getDate("date_of_birth")?.toLocalDate(),
            nationality = rs.getString("nationality"),
            status = UserStatus.fromValue(rs.getString("status")),
            region = rs.getString("region"),
            role = rs.getString("role") ?: "USER",
            totpSecret = rs.getString("totp_secret"),
            totpEnabled = rs.getBoolean("totp_enabled"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    fun save(user: User): User {
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, first_name, last_name,
                date_of_birth, nationality, status, region, role, totp_secret, totp_enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            user.id, user.email, user.phone, user.passwordHash,
            user.firstName, user.lastName, user.dateOfBirth, user.nationality,
            user.status.value, user.region, user.role, user.totpSecret, user.totpEnabled
        )
        return user
    }

    fun update(user: User): User {
        jdbcTemplate.update(
            """
            UPDATE identity.users SET
                email = ?, phone = ?, first_name = ?, last_name = ?,
                date_of_birth = ?, nationality = ?, status = ?,
                role = ?, totp_secret = ?, totp_enabled = ?, updated_at = now()
            WHERE id = ?
            """,
            user.email, user.phone, user.firstName, user.lastName,
            user.dateOfBirth, user.nationality, user.status.value,
            user.role, user.totpSecret, user.totpEnabled, user.id
        )
        return user
    }

    fun findById(id: UUID): User? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.users WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun findByEmail(email: String): User? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.users WHERE email = ?", rowMapper, email
        ).firstOrNull()
    }

    fun findByPhone(phone: String): User? {
        return jdbcTemplate.query(
            "SELECT * FROM identity.users WHERE phone = ?", rowMapper, phone
        ).firstOrNull()
    }

    fun existsByEmail(email: String): Boolean {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.users WHERE email = ?",
            Int::class.java, email
        )!! > 0
    }

    fun existsByPhone(phone: String): Boolean {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.users WHERE phone = ?",
            Int::class.java, phone
        )!! > 0
    }

    fun updateStatus(id: UUID, status: UserStatus) {
        jdbcTemplate.update(
            "UPDATE identity.users SET status = ?, updated_at = now() WHERE id = ?",
            status.value, id
        )
    }

    fun updateRole(id: UUID, role: String) {
        jdbcTemplate.update(
            "UPDATE identity.users SET role = ?, updated_at = now() WHERE id = ?",
            role, id
        )
    }
}

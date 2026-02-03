package com.ova.platform.shared.api

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
class AdminDashboardController(
    private val jdbcTemplate: JdbcTemplate
) {

    @GetMapping("/metrics")
    fun getMetrics(): ResponseEntity<DashboardMetricsResponse> {
        val totalUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.users",
            Long::class.java
        ) ?: 0L

        val pendingKyc = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.kyc_verifications WHERE status = 'pending'",
            Long::class.java
        ) ?: 0L

        val activeCases = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM shared.compliance_cases WHERE status IN ('open', 'under_review')",
            Long::class.java
        ) ?: 0L

        val now = Instant.now()
        val twentyFourHoursAgo = now.minus(24, ChronoUnit.HOURS)

        val transactionVolume24h = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entries e
            JOIN ledger.transactions t ON e.transaction_id = t.id
            WHERE t.created_at >= ?
              AND e.direction = 'debit'
            """,
            BigDecimal::class.java,
            Timestamp.from(twentyFourHoursAgo)
        ) ?: BigDecimal.ZERO

        // Calculate trends (compare to previous 24h period)
        val fortyEightHoursAgo = now.minus(48, ChronoUnit.HOURS)

        val prevUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.users WHERE created_at < ?",
            Long::class.java,
            Timestamp.from(twentyFourHoursAgo)
        ) ?: 0L

        val prevKyc = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.kyc_verifications WHERE status = 'pending' AND created_at < ?",
            Long::class.java,
            Timestamp.from(twentyFourHoursAgo)
        ) ?: 0L

        val prevCases = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM shared.compliance_cases
            WHERE status IN ('open', 'under_review') AND created_at < ?
            """,
            Long::class.java,
            Timestamp.from(twentyFourHoursAgo)
        ) ?: 0L

        val prevVolume = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM ledger.entries e
            JOIN ledger.transactions t ON e.transaction_id = t.id
            WHERE t.created_at >= ? AND t.created_at < ?
              AND e.direction = 'debit'
            """,
            BigDecimal::class.java,
            Timestamp.from(fortyEightHoursAgo),
            Timestamp.from(twentyFourHoursAgo)
        ) ?: BigDecimal.ZERO

        val response = DashboardMetricsResponse(
            totalUsers = totalUsers,
            totalUsersTrend = calculateTrend(prevUsers.toDouble(), totalUsers.toDouble()),
            pendingKyc = pendingKyc,
            pendingKycTrend = calculateTrend(prevKyc.toDouble(), pendingKyc.toDouble()),
            activeCases = activeCases,
            activeCasesTrend = calculateTrend(prevCases.toDouble(), activeCases.toDouble()),
            transactionVolume = transactionVolume24h,
            transactionVolumeTrend = calculateTrend(prevVolume.toDouble(), transactionVolume24h.toDouble()),
            transactionVolumeCurrency = "TRY"
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/activity")
    fun getRecentActivity(@RequestParam(defaultValue = "20") limit: Int): ResponseEntity<List<ActivityItemResponse>> {
        val clampedLimit = limit.coerceIn(1, 100)

        val activities = jdbcTemplate.query(
            """
            SELECT id, actor_id, actor_type, action, resource_type, resource_id, details, created_at
            FROM shared.audit_log
            ORDER BY created_at DESC
            LIMIT ?
            """,
            { rs, _ ->
                val action = rs.getString("action")
                val resourceType = rs.getString("resource_type")
                val resourceId = rs.getString("resource_id")
                val actorType = rs.getString("actor_type")

                ActivityItemResponse(
                    id = rs.getLong("id").toString(),
                    type = mapActionToType(action, resourceType),
                    message = formatActivityMessage(action, resourceType, resourceId),
                    timestamp = rs.getTimestamp("created_at").toInstant().toString(),
                    actor = rs.getString("actor_id") ?: actorType
                )
            },
            clampedLimit
        )

        return ResponseEntity.ok(activities)
    }

    private fun calculateTrend(previous: Double, current: Double): Double {
        if (previous == 0.0) return if (current > 0.0) 100.0 else 0.0
        return ((current - previous) / previous * 100).let {
            Math.round(it * 10.0) / 10.0
        }
    }

    private fun mapActionToType(action: String, resourceType: String): String {
        return when {
            action.contains("kyc") -> "kyc"
            action.contains("compliance") || resourceType == "compliance_case" -> "compliance"
            action.contains("user") || resourceType == "user" -> "user"
            action.contains("transfer") || action.contains("payment") || resourceType == "transaction" -> "transaction"
            else -> "system"
        }
    }

    private fun formatActivityMessage(action: String, resourceType: String, resourceId: String): String {
        return when (action) {
            "approve_kyc" -> "KYC verification $resourceId approved"
            "reject_kyc" -> "KYC verification $resourceId rejected"
            "initiate_kyc" -> "KYC verification $resourceId initiated"
            "create_compliance_case" -> "Compliance case $resourceId created"
            "resolve_compliance_case" -> "Compliance case $resourceId resolved"
            "suspend_user" -> "User $resourceId suspended"
            "reactivate_user" -> "User $resourceId reactivated"
            "update_profile" -> "User $resourceId updated profile"
            "freeze_account" -> "Account $resourceId frozen"
            "unfreeze_account" -> "Account $resourceId unfrozen"
            else -> "$action on $resourceType $resourceId"
        }
    }
}

data class DashboardMetricsResponse(
    val totalUsers: Long,
    val totalUsersTrend: Double,
    val pendingKyc: Long,
    val pendingKycTrend: Double,
    val activeCases: Long,
    val activeCasesTrend: Double,
    val transactionVolume: BigDecimal,
    val transactionVolumeTrend: Double,
    val transactionVolumeCurrency: String
)

data class ActivityItemResponse(
    val id: String,
    val type: String,
    val message: String,
    val timestamp: String,
    val actor: String?
)

package com.ova.platform.shared.api

import com.ova.platform.payments.internal.repository.ReconciliationRepository
import com.ova.platform.payments.internal.service.ReconciliationService
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
class AdminDashboardController(
    private val jdbcTemplate: JdbcTemplate,
    private val reconciliationRepository: ReconciliationRepository,
    private val reconciliationService: ReconciliationService
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

    @GetMapping("/audit-log")
    fun getAuditLog(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) actorType: String?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "created_at") sortBy: String,
        @RequestParam(defaultValue = "desc") sortOrder: String
    ): ResponseEntity<AuditLogResponse> {
        val clampedPage = page.coerceAtLeast(1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val offset = (clampedPage - 1) * clampedPageSize

        val allowedSortColumns = setOf("created_at", "action", "actor_type", "resource_type")
        val safeSortBy = if (sortBy in allowedSortColumns) sortBy else "created_at"
        val safeSortOrder = if (sortOrder.lowercase() == "asc") "ASC" else "DESC"

        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!action.isNullOrBlank()) {
            conditions.add("al.action = ?")
            params.add(action)
        }

        if (!actorType.isNullOrBlank()) {
            conditions.add("al.actor_type = ?")
            params.add(actorType)
        }

        if (!resourceType.isNullOrBlank()) {
            conditions.add("al.resource_type = ?")
            params.add(resourceType)
        }

        if (!search.isNullOrBlank()) {
            conditions.add("(al.action ILIKE ? OR al.resource_id ILIKE ? OR CAST(al.details AS TEXT) ILIKE ?)")
            val pattern = "%$search%"
            params.add(pattern)
            params.add(pattern)
            params.add(pattern)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")

        val countSql = "SELECT COUNT(*) FROM shared.audit_log al $whereClause"
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L

        val querySql = """
            SELECT al.id, al.actor_id, al.actor_type, al.action, al.resource_type,
                   al.resource_id, al.details, al.ip_address, al.created_at
            FROM shared.audit_log al
            $whereClause
            ORDER BY al.$safeSortBy $safeSortOrder
            LIMIT ? OFFSET ?
        """
        val queryParams = params.toMutableList()
        queryParams.add(clampedPageSize)
        queryParams.add(offset)

        val items = jdbcTemplate.query(querySql, { rs, _ ->
            AuditLogEntryResponse(
                id = rs.getLong("id").toString(),
                actorId = rs.getString("actor_id"),
                actorType = rs.getString("actor_type"),
                action = rs.getString("action"),
                resourceType = rs.getString("resource_type"),
                resourceId = rs.getString("resource_id"),
                details = rs.getString("details"),
                ipAddress = rs.getString("ip_address"),
                createdAt = rs.getTimestamp("created_at").toInstant().toString()
            )
        }, *queryParams.toTypedArray())

        return ResponseEntity.ok(
            AuditLogResponse(
                items = items,
                total = total,
                page = clampedPage,
                pageSize = clampedPageSize
            )
        )
    }

    @GetMapping("/reconciliation")
    fun getReconciliation(
        @RequestParam(required = false) date: String?
    ): ResponseEntity<List<ReconciliationResponse>> {
        val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now().minusDays(1)
        val reconciliations = reconciliationRepository.findReconciliationsByDate(targetDate)

        return ResponseEntity.ok(reconciliations.map {
            ReconciliationResponse(
                id = it.id!!,
                railProvider = it.railProvider,
                reconciliationDate = it.reconciliationDate.toString(),
                expectedAmount = it.expectedAmount.toPlainString(),
                actualAmount = it.actualAmount.toPlainString(),
                currency = it.currency,
                discrepancy = it.discrepancy.toPlainString(),
                matchedCount = it.matchedCount,
                unmatchedCount = it.unmatchedCount,
                status = it.status,
                notes = it.notes
            )
        })
    }

    @GetMapping("/reconciliation/discrepancies")
    fun getDiscrepancies(): ResponseEntity<List<ReconciliationResponse>> {
        val discrepancies = reconciliationRepository.findDiscrepancies()

        return ResponseEntity.ok(discrepancies.map {
            ReconciliationResponse(
                id = it.id!!,
                railProvider = it.railProvider,
                reconciliationDate = it.reconciliationDate.toString(),
                expectedAmount = it.expectedAmount.toPlainString(),
                actualAmount = it.actualAmount.toPlainString(),
                currency = it.currency,
                discrepancy = it.discrepancy.toPlainString(),
                matchedCount = it.matchedCount,
                unmatchedCount = it.unmatchedCount,
                status = it.status,
                notes = it.notes
            )
        })
    }

    @PostMapping("/reconciliation/{id}/resolve")
    fun resolveDiscrepancy(
        @PathVariable id: Long,
        @RequestBody request: ResolveDiscrepancyRequest
    ): ResponseEntity<Map<String, String>> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        reconciliationRepository.resolveReconciliation(id, adminId, request.notes)
        return ResponseEntity.ok(mapOf("status" to "resolved"))
    }

    @GetMapping("/safeguarding")
    fun getSafeguardingBalances(
        @RequestParam(required = false) date: String?
    ): ResponseEntity<List<SafeguardingBalanceResponse>> {
        val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
        val balances = reconciliationRepository.findSafeguardingBalancesByDate(targetDate)

        return ResponseEntity.ok(balances.map {
            SafeguardingBalanceResponse(
                id = it.id!!,
                currency = it.currency,
                region = it.region,
                bankName = it.bankName,
                bankAccount = it.bankAccount,
                ledgerBalance = it.ledgerBalance.toPlainString(),
                bankBalance = it.bankBalance.toPlainString(),
                discrepancy = it.discrepancy.toPlainString(),
                asOfDate = it.asOfDate.toString(),
                reconciled = it.reconciled
            )
        })
    }

    @PostMapping("/reconciliation/run")
    fun triggerReconciliation(
        @RequestParam(required = false) date: String?
    ): ResponseEntity<Map<String, String>> {
        val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now().minusDays(1)
        reconciliationService.reconcileRailSettlements(targetDate)
        reconciliationService.reconcileSafeguardingBalances(targetDate)
        return ResponseEntity.ok(mapOf("status" to "completed", "date" to targetDate.toString()))
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

data class AuditLogEntryResponse(
    val id: String,
    val actorId: String?,
    val actorType: String,
    val action: String,
    val resourceType: String,
    val resourceId: String,
    val details: String?,
    val ipAddress: String?,
    val createdAt: String
)

data class AuditLogResponse(
    val items: List<AuditLogEntryResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

data class ReconciliationResponse(
    val id: Long,
    val railProvider: String,
    val reconciliationDate: String,
    val expectedAmount: String,
    val actualAmount: String,
    val currency: String,
    val discrepancy: String,
    val matchedCount: Int,
    val unmatchedCount: Int,
    val status: String,
    val notes: String?
)

data class SafeguardingBalanceResponse(
    val id: Long,
    val currency: String,
    val region: String,
    val bankName: String,
    val bankAccount: String,
    val ledgerBalance: String,
    val bankBalance: String,
    val discrepancy: String,
    val asOfDate: String,
    val reconciled: Boolean
)

data class ResolveDiscrepancyRequest(
    val notes: String? = null
)

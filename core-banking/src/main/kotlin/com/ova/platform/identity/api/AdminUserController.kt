package com.ova.platform.identity.api

import com.ova.platform.identity.internal.service.UserService
import com.ova.platform.ledger.internal.model.AccountStatus
import com.ova.platform.ledger.internal.repository.AccountRepository
import com.ova.platform.shared.exception.NotFoundException
import com.ova.platform.shared.security.AuditService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(
    private val userService: UserService,
    private val accountRepository: AccountRepository,
    private val auditService: AuditService,
    private val jdbcTemplate: JdbcTemplate
) {

    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") pageSize: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) kycStatus: String?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "created_at") sortBy: String,
        @RequestParam(defaultValue = "desc") sortOrder: String
    ): ResponseEntity<UserListResponse> {
        val clampedPage = page.coerceAtLeast(1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val offset = (clampedPage - 1) * clampedPageSize

        val allowedSortColumns = setOf("created_at", "email", "status", "region")
        val safeSortBy = if (sortBy in allowedSortColumns) "u.$sortBy" else "u.created_at"
        val safeSortOrder = if (sortOrder.lowercase() == "asc") "ASC" else "DESC"

        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!status.isNullOrBlank()) {
            conditions.add("u.status = ?")
            params.add(status)
        }

        if (!region.isNullOrBlank()) {
            conditions.add("u.region = ?")
            params.add(region)
        }

        if (!search.isNullOrBlank()) {
            conditions.add("(u.email ILIKE ? OR u.phone ILIKE ? OR u.first_name ILIKE ? OR u.last_name ILIKE ?)")
            val pattern = "%$search%"
            params.add(pattern)
            params.add(pattern)
            params.add(pattern)
            params.add(pattern)
        }

        if (!kycStatus.isNullOrBlank()) {
            conditions.add("""
                EXISTS (
                    SELECT 1 FROM identity.kyc_verifications kv
                    WHERE kv.user_id = u.id AND kv.status = ?
                    AND kv.created_at = (SELECT MAX(kv2.created_at) FROM identity.kyc_verifications kv2 WHERE kv2.user_id = u.id)
                )
            """)
            params.add(kycStatus)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")

        val countSql = """
            SELECT COUNT(*)
            FROM identity.users u
            $whereClause
        """
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.toTypedArray()) ?: 0L

        val querySql = """
            SELECT u.id, u.email, u.phone, u.first_name, u.last_name, u.status, u.region,
                   u.created_at, u.updated_at,
                   (SELECT kv.status FROM identity.kyc_verifications kv
                    WHERE kv.user_id = u.id ORDER BY kv.created_at DESC LIMIT 1) as kyc_status
            FROM identity.users u
            $whereClause
            ORDER BY $safeSortBy $safeSortOrder
            LIMIT ? OFFSET ?
        """
        val queryParams = params.toMutableList()
        queryParams.add(clampedPageSize)
        queryParams.add(offset)

        val users = jdbcTemplate.query(querySql, { rs, _ ->
            val userId = UUID.fromString(rs.getString("id"))
            val firstName = rs.getString("first_name")
            val lastName = rs.getString("last_name")
            val name = when {
                firstName != null && lastName != null -> "$firstName $lastName"
                firstName != null -> firstName
                lastName != null -> lastName
                else -> rs.getString("email")
            }

            AdminUserResponse(
                id = userId.toString(),
                name = name,
                email = rs.getString("email"),
                phone = rs.getString("phone"),
                status = rs.getString("status"),
                kycStatus = rs.getString("kyc_status") ?: "pending",
                region = rs.getString("region"),
                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                lastLoginAt = null,
                accounts = emptyList()
            )
        }, *queryParams.toTypedArray())

        // Fetch accounts for all users in the page
        val userIds = users.map { UUID.fromString(it.id) }
        val accountsByUserId = if (userIds.isNotEmpty()) {
            fetchAccountsForUsers(userIds)
        } else {
            emptyMap()
        }

        val usersWithAccounts = users.map { user ->
            user.copy(accounts = accountsByUserId[UUID.fromString(user.id)] ?: emptyList())
        }

        return ResponseEntity.ok(
            UserListResponse(
                items = usersWithAccounts,
                total = total,
                page = clampedPage,
                pageSize = clampedPageSize
            )
        )
    }

    @PostMapping("/{userId}/suspend")
    @Transactional
    fun suspendUser(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: SuspendUserRequest
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        userService.suspendUser(userId, adminId)
        auditService.log(
            actorId = adminId,
            actorType = "admin",
            action = "suspend_user",
            resourceType = "user",
            resourceId = userId.toString(),
            details = mapOf("reason" to request.reason)
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{userId}/reactivate")
    @Transactional
    fun reactivateUser(@PathVariable userId: UUID): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        userService.reactivateUser(userId, adminId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{userId}/accounts/{accountId}/freeze")
    @Transactional
    fun freezeAccount(
        @PathVariable userId: UUID,
        @PathVariable accountId: UUID,
        @Valid @RequestBody request: FreezeAccountRequest
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val account = accountRepository.findById(accountId)
            ?: throw NotFoundException("Account", accountId.toString())

        if (account.userId != userId) {
            throw NotFoundException("Account", accountId.toString())
        }

        accountRepository.updateStatus(accountId, AccountStatus.FROZEN)
        auditService.log(
            actorId = adminId,
            actorType = "admin",
            action = "freeze_account",
            resourceType = "account",
            resourceId = accountId.toString(),
            details = mapOf("userId" to userId.toString(), "reason" to request.reason)
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{userId}/accounts/{accountId}/unfreeze")
    @Transactional
    fun unfreezeAccount(
        @PathVariable userId: UUID,
        @PathVariable accountId: UUID
    ): ResponseEntity<Void> {
        val adminId = UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
        val account = accountRepository.findById(accountId)
            ?: throw NotFoundException("Account", accountId.toString())

        if (account.userId != userId) {
            throw NotFoundException("Account", accountId.toString())
        }

        accountRepository.updateStatus(accountId, AccountStatus.ACTIVE)
        auditService.log(
            actorId = adminId,
            actorType = "admin",
            action = "unfreeze_account",
            resourceType = "account",
            resourceId = accountId.toString(),
            details = mapOf("userId" to userId.toString())
        )
        return ResponseEntity.ok().build()
    }

    private fun fetchAccountsForUsers(userIds: List<UUID>): Map<UUID, List<UserAccountResponse>> {
        if (userIds.isEmpty()) return emptyMap()

        val placeholders = userIds.joinToString(",") { "?" }
        val sql = """
            SELECT a.id, a.user_id, a.currency, a.status,
                   COALESCE(
                       (SELECT e.balance_after FROM ledger.entries e
                        WHERE e.account_id = a.id ORDER BY e.created_at DESC, e.id DESC LIMIT 1),
                       0
                   ) as balance
            FROM ledger.accounts a
            WHERE a.user_id IN ($placeholders)
              AND a.account_type = 'user_wallet'
            ORDER BY a.currency
        """

        val accounts = jdbcTemplate.query(sql, { rs, _ ->
            val uid = UUID.fromString(rs.getString("user_id"))
            uid to UserAccountResponse(
                id = rs.getString("id"),
                currency = rs.getString("currency"),
                balance = rs.getBigDecimal("balance"),
                status = rs.getString("status"),
                iban = null
            )
        }, *userIds.toTypedArray())

        return accounts.groupBy({ it.first }, { it.second })
    }
}

data class SuspendUserRequest(
    @field:NotBlank val reason: String
)

data class FreezeAccountRequest(
    @field:NotBlank val reason: String
)

data class UserAccountResponse(
    val id: String,
    val currency: String,
    val balance: BigDecimal,
    val status: String,
    val iban: String?
)

data class AdminUserResponse(
    val id: String,
    val name: String,
    val email: String,
    val phone: String?,
    val status: String,
    val kycStatus: String,
    val region: String,
    val createdAt: String,
    val lastLoginAt: String?,
    val accounts: List<UserAccountResponse>
)

data class UserListResponse(
    val items: List<AdminUserResponse>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

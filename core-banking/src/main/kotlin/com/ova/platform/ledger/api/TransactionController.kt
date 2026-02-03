package com.ova.platform.ledger.api

import com.ova.platform.ledger.internal.model.Entry
import com.ova.platform.ledger.internal.model.Transaction
import com.ova.platform.ledger.internal.service.AccountService
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.ledger.internal.service.Statement
import com.ova.platform.ledger.internal.service.TransactionService
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.ForbiddenException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class TransactionController(
    private val transactionService: TransactionService,
    private val ledgerService: LedgerService,
    private val accountService: AccountService
) {

    @GetMapping("/accounts/{accountId}/transactions")
    fun getAccountTransactions(
        @PathVariable accountId: UUID,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<PagedResponse<TransactionResponse>> {
        val userId = getAuthenticatedUserId()
        validateAccountOwnership(accountId, userId)

        val parsedFrom = from?.let { parseInstant(it, "from") }
        val parsedTo = to?.let { parseInstant(it, "to") }

        val result = transactionService.getTransactions(
            accountId = accountId,
            type = type,
            from = parsedFrom,
            to = parsedTo,
            limit = limit.coerceIn(1, 200),
            offset = offset.coerceAtLeast(0)
        )

        val transactionResponses = result.items.map { tx ->
            val entries = ledgerService.getEntries(tx.id)
            toTransactionResponse(tx, entries)
        }

        return ResponseEntity.ok(
            PagedResponse(
                items = transactionResponses,
                total = result.total,
                limit = result.limit,
                offset = result.offset
            )
        )
    }

    @GetMapping("/accounts/{accountId}/statement")
    fun getAccountStatement(
        @PathVariable accountId: UUID,
        @RequestParam from: String,
        @RequestParam to: String
    ): ResponseEntity<StatementResponse> {
        val userId = getAuthenticatedUserId()
        validateAccountOwnership(accountId, userId)

        val parsedFrom = parseInstant(from, "from")
        val parsedTo = parseInstant(to, "to")

        if (parsedFrom.isAfter(parsedTo)) {
            throw BadRequestException("'from' must be before 'to'")
        }

        val statement = transactionService.getStatement(
            accountId = accountId,
            from = parsedFrom,
            to = parsedTo
        )

        return ResponseEntity.ok(toStatementResponse(statement))
    }

    @GetMapping("/transactions/{transactionId}")
    fun getTransaction(@PathVariable transactionId: UUID): ResponseEntity<TransactionDetailResponse> {
        val transaction = transactionService.getTransactionById(transactionId)
        val entries = ledgerService.getEntries(transactionId)
        return ResponseEntity.ok(
            TransactionDetailResponse(
                transactionId = transaction.id.toString(),
                type = transaction.type.value,
                status = transaction.status.value,
                referenceId = transaction.referenceId,
                metadata = transaction.metadata,
                createdAt = transaction.createdAt.toString(),
                completedAt = transaction.completedAt?.toString(),
                entries = entries.map { toEntryResponse(it) }
            )
        )
    }

    private fun getAuthenticatedUserId(): UUID {
        return UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)
    }

    private fun validateAccountOwnership(accountId: UUID, userId: UUID) {
        val account = accountService.getAccountById(accountId)
        if (account.userId != userId) {
            throw ForbiddenException("You do not have access to this account")
        }
    }

    private fun parseInstant(value: String, paramName: String): Instant {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            throw BadRequestException("Invalid ISO datetime for '$paramName': $value")
        }
    }

    private fun toTransactionResponse(tx: Transaction, entries: List<Entry>): TransactionResponse {
        return TransactionResponse(
            id = tx.id.toString(),
            type = tx.type.value,
            status = tx.status.value,
            referenceId = tx.referenceId,
            metadata = tx.metadata,
            createdAt = tx.createdAt.toString(),
            completedAt = tx.completedAt?.toString(),
            entries = entries.map { toEntryResponse(it) }
        )
    }

    private fun toEntryResponse(entry: Entry): EntryResponse {
        return EntryResponse(
            id = entry.id.toString(),
            accountId = entry.accountId.toString(),
            direction = entry.direction.value,
            amount = entry.amount.toPlainString(),
            currency = entry.currency,
            balanceAfter = entry.balanceAfter.toPlainString(),
            createdAt = entry.createdAt.toString()
        )
    }

    private fun toStatementResponse(statement: Statement): StatementResponse {
        return StatementResponse(
            accountId = statement.accountId.toString(),
            currency = statement.currency,
            periodFrom = statement.periodFrom.toString(),
            periodTo = statement.periodTo.toString(),
            openingBalance = statement.openingBalance.toPlainString(),
            closingBalance = statement.closingBalance.toPlainString(),
            transactionCount = statement.transactionCount,
            transactions = statement.transactions.map { tx ->
                TransactionResponse(
                    id = tx.id.toString(),
                    type = tx.type.value,
                    status = tx.status.value,
                    referenceId = tx.referenceId,
                    metadata = tx.metadata,
                    createdAt = tx.createdAt.toString(),
                    completedAt = tx.completedAt?.toString(),
                    entries = emptyList()
                )
            }
        )
    }
}

data class PagedResponse<T>(
    val items: List<T>,
    val total: Long,
    val limit: Int,
    val offset: Int
)

data class TransactionResponse(
    val id: String,
    val type: String,
    val status: String,
    val referenceId: String?,
    val metadata: Map<String, Any>?,
    val createdAt: String,
    val completedAt: String?,
    val entries: List<EntryResponse>
)

data class TransactionDetailResponse(
    val transactionId: String,
    val type: String,
    val status: String,
    val referenceId: String?,
    val metadata: Map<String, Any>?,
    val createdAt: String,
    val completedAt: String?,
    val entries: List<EntryResponse>
)

data class EntryResponse(
    val id: String,
    val accountId: String,
    val direction: String,
    val amount: String,
    val currency: String,
    val balanceAfter: String,
    val createdAt: String
)

data class StatementResponse(
    val accountId: String,
    val currency: String,
    val periodFrom: String,
    val periodTo: String,
    val openingBalance: String,
    val closingBalance: String,
    val transactionCount: Int,
    val transactions: List<TransactionResponse>
)

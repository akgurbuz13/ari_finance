package com.ova.platform.ledger.api

import com.ova.platform.ledger.internal.service.LedgerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController(private val ledgerService: LedgerService) {

    @GetMapping("/account/{accountId}")
    fun getTransactionHistory(
        @PathVariable accountId: UUID,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<List<TransactionResponse>> {
        val transactions = ledgerService.getTransactionHistory(accountId, limit, offset)
        return ResponseEntity.ok(
            transactions.map {
                TransactionResponse(
                    id = it.id.toString(),
                    type = it.type.value,
                    status = it.status.value,
                    referenceId = it.referenceId,
                    metadata = it.metadata,
                    createdAt = it.createdAt.toString(),
                    completedAt = it.completedAt?.toString()
                )
            }
        )
    }

    @GetMapping("/{transactionId}")
    fun getTransaction(@PathVariable transactionId: UUID): ResponseEntity<TransactionDetailResponse> {
        val entries = ledgerService.getEntries(transactionId)
        return ResponseEntity.ok(
            TransactionDetailResponse(
                transactionId = transactionId.toString(),
                entries = entries.map {
                    EntryResponse(
                        id = it.id.toString(),
                        accountId = it.accountId.toString(),
                        direction = it.direction.value,
                        amount = it.amount.toPlainString(),
                        currency = it.currency,
                        balanceAfter = it.balanceAfter.toPlainString(),
                        createdAt = it.createdAt.toString()
                    )
                }
            )
        )
    }
}

data class TransactionResponse(
    val id: String,
    val type: String,
    val status: String,
    val referenceId: String?,
    val metadata: Map<String, Any>?,
    val createdAt: String,
    val completedAt: String?
)

data class TransactionDetailResponse(
    val transactionId: String,
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

package com.ova.platform.ledger.internal.service

import com.ova.platform.ledger.internal.model.Transaction
import com.ova.platform.ledger.internal.repository.EntryRepository
import com.ova.platform.ledger.internal.repository.TransactionRepository
import com.ova.platform.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PagedResult<T>(
    val items: List<T>,
    val total: Long,
    val limit: Int,
    val offset: Int
)

data class Statement(
    val accountId: UUID,
    val currency: String,
    val periodFrom: Instant,
    val periodTo: Instant,
    val openingBalance: BigDecimal,
    val closingBalance: BigDecimal,
    val transactionCount: Int,
    val transactions: List<Transaction>
)

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val entryRepository: EntryRepository,
    private val accountService: AccountService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getTransactions(
        accountId: UUID,
        type: String?,
        from: Instant?,
        to: Instant?,
        limit: Int,
        offset: Int
    ): PagedResult<Transaction> {
        val items = transactionRepository.findByAccountIdAndFilters(
            accountId = accountId,
            type = type,
            from = from,
            to = to,
            limit = limit,
            offset = offset
        )
        val total = transactionRepository.countByAccountIdAndFilters(
            accountId = accountId,
            type = type,
            from = from,
            to = to
        )

        log.debug(
            "Fetched transactions for account={}, type={}, from={}, to={}, count={}/{}",
            accountId, type, from, to, items.size, total
        )

        return PagedResult(
            items = items,
            total = total,
            limit = limit,
            offset = offset
        )
    }

    fun getTransactionById(transactionId: UUID): Transaction {
        return transactionRepository.findById(transactionId)
            ?: throw NotFoundException("Transaction", transactionId.toString())
    }

    fun userCanAccessTransaction(transactionId: UUID, userId: UUID): Boolean {
        return transactionRepository.userHasAccessToTransaction(transactionId, userId)
    }

    fun getStatement(
        accountId: UUID,
        from: Instant,
        to: Instant
    ): Statement {
        val account = accountService.getAccountById(accountId)

        val openingBalance = entryRepository.getBalanceAt(accountId, from)
        val closingBalance = entryRepository.getBalanceAt(accountId, to)

        val transactions = transactionRepository.findByAccountIdAndFilters(
            accountId = accountId,
            type = null,
            from = from,
            to = to,
            limit = Int.MAX_VALUE,
            offset = 0
        )

        log.debug(
            "Generated statement for account={}, period={} to {}, txCount={}",
            accountId, from, to, transactions.size
        )

        return Statement(
            accountId = accountId,
            currency = account.currency,
            periodFrom = from,
            periodTo = to,
            openingBalance = openingBalance,
            closingBalance = closingBalance,
            transactionCount = transactions.size,
            transactions = transactions
        )
    }
}

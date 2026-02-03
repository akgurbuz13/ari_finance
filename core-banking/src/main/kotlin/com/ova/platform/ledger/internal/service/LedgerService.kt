package com.ova.platform.ledger.internal.service

import com.ova.platform.ledger.event.EntryPosted
import com.ova.platform.ledger.internal.model.*
import com.ova.platform.ledger.internal.repository.AccountRepository
import com.ova.platform.ledger.internal.repository.EntryRepository
import com.ova.platform.ledger.internal.repository.TransactionRepository
import com.ova.platform.shared.event.OutboxPublisher
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.InsufficientFundsException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class LedgerService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val entryRepository: EntryRepository,
    private val outboxPublisher: OutboxPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Post a set of balanced entries atomically. This is the core of the double-entry engine.
     *
     * Rules:
     * 1. Sum of debits MUST equal sum of credits per currency
     * 2. All accounts must exist and be active
     * 3. Debit entries must not cause negative balances (for user_wallet accounts)
     * 4. Idempotency key prevents double-posting
     */
    @Transactional
    fun postEntries(
        idempotencyKey: String,
        type: TransactionType,
        postings: List<PostingInstruction>,
        referenceId: String? = null,
        metadata: Map<String, Any>? = null
    ): Transaction {
        // Idempotency check
        val existing = transactionRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            log.info("Idempotent replay for key={}, txId={}", idempotencyKey, existing.id)
            return existing
        }

        // Validate balance: debits == credits per currency
        validateBalance(postings)

        // Create transaction
        val transaction = transactionRepository.save(
            Transaction(
                idempotencyKey = idempotencyKey,
                type = type,
                status = TransactionStatus.COMPLETED,
                referenceId = referenceId,
                metadata = metadata,
                completedAt = Instant.now()
            )
        )

        // Post entries with balance snapshots
        for (posting in postings) {
            val account = accountRepository.findById(posting.accountId)
                ?: throw BadRequestException("Account not found: ${posting.accountId}")

            if (account.status != AccountStatus.ACTIVE) {
                throw BadRequestException("Account ${posting.accountId} is ${account.status.value}")
            }

            val currentBalance = entryRepository.getLatestBalance(posting.accountId)

            val newBalance = when (posting.direction) {
                EntryDirection.CREDIT -> currentBalance.add(posting.amount)
                EntryDirection.DEBIT -> {
                    val result = currentBalance.subtract(posting.amount)
                    // Only user wallets have non-negative constraint
                    if (result < BigDecimal.ZERO && account.accountType == AccountType.USER_WALLET) {
                        throw InsufficientFundsException()
                    }
                    result
                }
            }

            entryRepository.save(
                Entry(
                    transactionId = transaction.id,
                    accountId = posting.accountId,
                    direction = posting.direction,
                    amount = posting.amount,
                    currency = posting.currency,
                    balanceAfter = newBalance
                )
            )
        }

        outboxPublisher.publish(
            EntryPosted(
                transactionId = transaction.id,
                type = type.value,
                entryCount = postings.size
            )
        )

        log.info("Posted transaction txId={}, type={}, entries={}", transaction.id, type, postings.size)
        return transaction
    }

    fun getBalance(accountId: UUID): BigDecimal {
        return entryRepository.getLatestBalance(accountId)
    }

    fun getTransactionHistory(accountId: UUID, limit: Int = 50, offset: Int = 0): List<Transaction> {
        return transactionRepository.findByAccountId(accountId, limit, offset)
    }

    fun getEntries(transactionId: UUID): List<Entry> {
        return entryRepository.findByTransactionId(transactionId)
    }

    private fun validateBalance(postings: List<PostingInstruction>) {
        val byCurrency = postings.groupBy { it.currency }

        for ((currency, entries) in byCurrency) {
            val totalDebits = entries
                .filter { it.direction == EntryDirection.DEBIT }
                .sumOf { it.amount }

            val totalCredits = entries
                .filter { it.direction == EntryDirection.CREDIT }
                .sumOf { it.amount }

            if (totalDebits.compareTo(totalCredits) != 0) {
                throw BadRequestException(
                    "Unbalanced postings for currency $currency: debits=$totalDebits, credits=$totalCredits"
                )
            }
        }
    }
}

package com.ova.platform.ledger

import com.ova.platform.BaseIntegrationTest
import com.ova.platform.TestConfig
import com.ova.platform.ledger.internal.model.*
import com.ova.platform.ledger.internal.repository.AccountRepository
import com.ova.platform.ledger.internal.repository.EntryRepository
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.InsufficientFundsException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

@Import(TestConfig::class)
class LedgerServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var ledgerService: LedgerService

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var entryRepository: EntryRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var userA: UUID
    private lateinit var userB: UUID
    private lateinit var accountA: Account
    private lateinit var accountB: Account
    private lateinit var systemFloat: Account

    @BeforeEach
    fun setUp() {
        // Clean data in reverse dependency order
        jdbcTemplate.execute("DELETE FROM ledger.entries")
        jdbcTemplate.execute("DELETE FROM ledger.transactions")
        jdbcTemplate.execute("DELETE FROM shared.outbox_events")
        jdbcTemplate.execute("DELETE FROM payments.payment_status_history")
        jdbcTemplate.execute("DELETE FROM payments.payment_orders")
        jdbcTemplate.execute("DELETE FROM ledger.accounts")
        jdbcTemplate.execute("DELETE FROM identity.kyc_verifications")
        jdbcTemplate.execute("DELETE FROM identity.refresh_tokens")
        jdbcTemplate.execute("DELETE FROM identity.users")
        jdbcTemplate.execute("DELETE FROM shared.audit_log")

        // Create test users
        userA = UUID.randomUUID()
        userB = UUID.randomUUID()
        createTestUser(userA, "usera@test.com", "+905551111111")
        createTestUser(userB, "userb@test.com", "+905552222222")

        // Create accounts
        accountA = accountRepository.save(
            Account(userId = userA, currency = "TRY", accountType = AccountType.USER_WALLET)
        )
        accountB = accountRepository.save(
            Account(userId = userB, currency = "TRY", accountType = AccountType.USER_WALLET)
        )
        systemFloat = accountRepository.save(
            Account(
                userId = UUID.fromString("00000000-0000-0000-0000-000000000000"),
                currency = "TRY",
                accountType = AccountType.SYSTEM_FLOAT
            )
        )

        // Seed accountA with 10,000 TRY via system float
        ledgerService.postEntries(
            idempotencyKey = "seed-${accountA.id}",
            type = TransactionType.DEPOSIT,
            postings = listOf(
                PostingInstruction(systemFloat.id, EntryDirection.DEBIT, BigDecimal("10000.00"), "TRY"),
                PostingInstruction(accountA.id, EntryDirection.CREDIT, BigDecimal("10000.00"), "TRY")
            )
        )
    }

    private fun createTestUser(id: UUID, email: String, phone: String) {
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, status, region)
            VALUES (?, ?, ?, 'hash', 'active', 'TR')
            """,
            id, email, phone
        )
    }

    @Nested
    inner class PostEntries {

        @Test
        fun `should post balanced entries and update balances`() {
            val tx = ledgerService.postEntries(
                idempotencyKey = "p2p-test-1",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("1000.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("1000.00"), "TRY")
                )
            )

            tx shouldNotBe null
            tx.type shouldBe TransactionType.P2P_TRANSFER
            tx.status shouldBe TransactionStatus.COMPLETED

            // Verify balances
            ledgerService.getBalance(accountA.id) shouldBeEqualComparingTo BigDecimal("9000.00")
            ledgerService.getBalance(accountB.id) shouldBeEqualComparingTo BigDecimal("1000.00")
        }

        @Test
        fun `should store correct balance_after snapshots`() {
            ledgerService.postEntries(
                idempotencyKey = "snapshot-test-1",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("3000.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("3000.00"), "TRY")
                )
            )

            val tx2 = ledgerService.postEntries(
                idempotencyKey = "snapshot-test-2",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("2000.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("2000.00"), "TRY")
                )
            )

            val entries = entryRepository.findByTransactionId(tx2.id)
            entries shouldHaveSize 2

            val debitEntry = entries.first { it.direction == EntryDirection.DEBIT }
            val creditEntry = entries.first { it.direction == EntryDirection.CREDIT }

            debitEntry.balanceAfter shouldBeEqualComparingTo BigDecimal("5000.00")
            creditEntry.balanceAfter shouldBeEqualComparingTo BigDecimal("5000.00")
        }

        @Test
        fun `should reject unbalanced postings`() {
            shouldThrow<BadRequestException> {
                ledgerService.postEntries(
                    idempotencyKey = "unbalanced-test",
                    type = TransactionType.P2P_TRANSFER,
                    postings = listOf(
                        PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("1000.00"), "TRY"),
                        PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("999.00"), "TRY")
                    )
                )
            }.message shouldBe "Unbalanced postings for currency TRY: debits=1000.00, credits=999.00"
        }

        @Test
        fun `should reject debit that would cause negative balance on user wallet`() {
            shouldThrow<InsufficientFundsException> {
                ledgerService.postEntries(
                    idempotencyKey = "overdraft-test",
                    type = TransactionType.P2P_TRANSFER,
                    postings = listOf(
                        PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("99999.00"), "TRY"),
                        PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("99999.00"), "TRY")
                    )
                )
            }

            // Balance should remain unchanged
            ledgerService.getBalance(accountA.id) shouldBeEqualComparingTo BigDecimal("10000.00")
        }

        @Test
        fun `should allow negative balance on system float accounts`() {
            val tx = ledgerService.postEntries(
                idempotencyKey = "system-float-negative",
                type = TransactionType.DEPOSIT,
                postings = listOf(
                    PostingInstruction(systemFloat.id, EntryDirection.DEBIT, BigDecimal("50000.00"), "TRY"),
                    PostingInstruction(accountA.id, EntryDirection.CREDIT, BigDecimal("50000.00"), "TRY")
                )
            )

            tx.status shouldBe TransactionStatus.COMPLETED
            // System float can go negative
            ledgerService.getBalance(systemFloat.id) shouldBeEqualComparingTo BigDecimal("-60000.00")
        }

        @Test
        fun `should reject posting to frozen account`() {
            accountRepository.updateStatus(accountB.id, AccountStatus.FROZEN)

            shouldThrow<BadRequestException> {
                ledgerService.postEntries(
                    idempotencyKey = "frozen-test",
                    type = TransactionType.P2P_TRANSFER,
                    postings = listOf(
                        PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("100.00"), "TRY"),
                        PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("100.00"), "TRY")
                    )
                )
            }.message shouldBe "Account ${accountB.id} is frozen"
        }
    }

    @Nested
    inner class Idempotency {

        @Test
        fun `should return same transaction for duplicate idempotency key`() {
            val tx1 = ledgerService.postEntries(
                idempotencyKey = "idempotent-test",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("500.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("500.00"), "TRY")
                )
            )

            val tx2 = ledgerService.postEntries(
                idempotencyKey = "idempotent-test",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("500.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("500.00"), "TRY")
                )
            )

            tx1.id shouldBe tx2.id
            // Balance should only reflect one debit
            ledgerService.getBalance(accountA.id) shouldBeEqualComparingTo BigDecimal("9500.00")
        }
    }

    @Nested
    inner class TransactionHistory {

        @Test
        fun `should return transactions for an account`() {
            ledgerService.postEntries(
                idempotencyKey = "history-1",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("100.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("100.00"), "TRY")
                )
            )
            ledgerService.postEntries(
                idempotencyKey = "history-2",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("200.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("200.00"), "TRY")
                )
            )

            // accountA has seed + 2 transfers = 3 transactions
            val history = ledgerService.getTransactionHistory(accountA.id)
            history shouldHaveSize 3
        }
    }

    @Nested
    inner class Entries {

        @Test
        fun `should return entries for a transaction`() {
            val tx = ledgerService.postEntries(
                idempotencyKey = "entries-test",
                type = TransactionType.P2P_TRANSFER,
                postings = listOf(
                    PostingInstruction(accountA.id, EntryDirection.DEBIT, BigDecimal("750.00"), "TRY"),
                    PostingInstruction(accountB.id, EntryDirection.CREDIT, BigDecimal("750.00"), "TRY")
                )
            )

            val entries = ledgerService.getEntries(tx.id)
            entries shouldHaveSize 2

            val debit = entries.first { it.direction == EntryDirection.DEBIT }
            debit.accountId shouldBe accountA.id
            debit.amount shouldBeEqualComparingTo BigDecimal("750.00")

            val credit = entries.first { it.direction == EntryDirection.CREDIT }
            credit.accountId shouldBe accountB.id
            credit.amount shouldBeEqualComparingTo BigDecimal("750.00")
        }
    }
}

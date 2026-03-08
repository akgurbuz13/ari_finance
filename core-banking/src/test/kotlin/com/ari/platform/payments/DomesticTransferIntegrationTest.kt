package com.ari.platform.payments

import com.ari.platform.BaseIntegrationTest
import com.ari.platform.TestConfig
import com.ari.platform.ledger.internal.model.*
import com.ari.platform.ledger.internal.repository.AccountRepository
import com.ari.platform.ledger.internal.service.LedgerService
import com.ari.platform.payments.internal.model.PaymentStatus
import com.ari.platform.payments.internal.repository.PaymentOrderRepository
import com.ari.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ari.platform.payments.internal.service.DomesticTransferService
import com.ari.platform.shared.exception.BadRequestException
import com.ari.platform.shared.exception.InsufficientFundsException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

@Import(TestConfig::class)
class DomesticTransferIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var domesticTransferService: DomesticTransferService

    @Autowired
    lateinit var ledgerService: LedgerService

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var paymentOrderRepository: PaymentOrderRepository

    @Autowired
    lateinit var statusHistoryRepository: PaymentStatusHistoryRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var senderUser: UUID
    private lateinit var receiverUser: UUID
    private lateinit var senderAccount: Account
    private lateinit var receiverAccount: Account
    private lateinit var systemFloat: Account

    @BeforeEach
    fun setUp() {
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

        senderUser = UUID.randomUUID()
        receiverUser = UUID.randomUUID()
        createTestUser(senderUser, "sender@test.com", "+905551111111")
        createTestUser(receiverUser, "receiver@test.com", "+905552222222")

        // System user for float account
        val systemUser = UUID.fromString("00000000-0000-0000-0000-000000000000")
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, status, region)
            VALUES (?, 'system@ari.com', '+900000000000', 'hash', 'active', 'TR')
            ON CONFLICT (id) DO NOTHING
            """,
            systemUser
        )

        senderAccount = accountRepository.save(
            Account(userId = senderUser, currency = "TRY", accountType = AccountType.USER_WALLET)
        )
        receiverAccount = accountRepository.save(
            Account(userId = receiverUser, currency = "TRY", accountType = AccountType.USER_WALLET)
        )
        systemFloat = accountRepository.save(
            Account(userId = systemUser, currency = "TRY", accountType = AccountType.SYSTEM_FLOAT)
        )

        // Seed sender with 5,000 TRY
        ledgerService.postEntries(
            idempotencyKey = "seed-${senderAccount.id}",
            type = TransactionType.DEPOSIT,
            postings = listOf(
                PostingInstruction(systemFloat.id, EntryDirection.DEBIT, BigDecimal("5000.00"), "TRY"),
                PostingInstruction(senderAccount.id, EntryDirection.CREDIT, BigDecimal("5000.00"), "TRY")
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

    @Test
    fun `should complete domestic P2P transfer end-to-end`() {
        val order = domesticTransferService.execute(
            idempotencyKey = "domestic-e2e-1",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("1500.00"),
            currency = "TRY",
            description = "Test transfer",
            initiatorId = senderUser
        )

        order shouldNotBe null
        order.status shouldBe PaymentStatus.COMPLETED
        order.ledgerTransactionId shouldNotBe null

        // Verify balances
        ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("3500.00")
        ledgerService.getBalance(receiverAccount.id) shouldBeEqualComparingTo BigDecimal("1500.00")

        // Verify status history records all transitions
        val history = statusHistoryRepository.findByPaymentOrderId(order.id)
        val statuses = history.map { it.toStatus }
        statuses shouldBe listOf(
            PaymentStatus.INITIATED,
            PaymentStatus.COMPLIANCE_CHECK,
            PaymentStatus.PROCESSING,
            PaymentStatus.SETTLING,
            PaymentStatus.COMPLETED
        )
    }

    @Test
    fun `should reject transfer with insufficient funds`() {
        shouldThrow<InsufficientFundsException> {
            domesticTransferService.execute(
                idempotencyKey = "insufficient-test",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("99999.00"),
                currency = "TRY",
                initiatorId = senderUser
            )
        }

        // Balances unchanged
        ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("5000.00")
        ledgerService.getBalance(receiverAccount.id) shouldBeEqualComparingTo BigDecimal("0")
    }

    @Test
    fun `should reject transfer to self`() {
        shouldThrow<BadRequestException> {
            domesticTransferService.execute(
                idempotencyKey = "self-transfer",
                senderAccountId = senderAccount.id,
                receiverAccountId = senderAccount.id,
                amount = BigDecimal("100.00"),
                currency = "TRY",
                initiatorId = senderUser
            )
        }.message shouldBe "Sender and receiver accounts must be different"
    }

    @Test
    fun `should reject transfer with zero amount`() {
        shouldThrow<BadRequestException> {
            domesticTransferService.execute(
                idempotencyKey = "zero-amount",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal.ZERO,
                currency = "TRY",
                initiatorId = senderUser
            )
        }.message shouldBe "Transfer amount must be positive"
    }

    @Test
    fun `should reject transfer from frozen account`() {
        accountRepository.updateStatus(senderAccount.id, AccountStatus.FROZEN)

        shouldThrow<BadRequestException> {
            domesticTransferService.execute(
                idempotencyKey = "frozen-sender",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("100.00"),
                currency = "TRY",
                initiatorId = senderUser
            )
        }
    }

    @Test
    fun `should reject transfer with currency mismatch`() {
        val eurAccount = accountRepository.save(
            Account(userId = receiverUser, currency = "EUR", accountType = AccountType.USER_WALLET)
        )

        shouldThrow<BadRequestException> {
            domesticTransferService.execute(
                idempotencyKey = "currency-mismatch",
                senderAccountId = senderAccount.id,
                receiverAccountId = eurAccount.id,
                amount = BigDecimal("100.00"),
                currency = "TRY",
                initiatorId = senderUser
            )
        }
    }

    @Test
    fun `should be idempotent on replay`() {
        val order1 = domesticTransferService.execute(
            idempotencyKey = "idempotent-domestic",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("500.00"),
            currency = "TRY",
            initiatorId = senderUser
        )

        val order2 = domesticTransferService.execute(
            idempotencyKey = "idempotent-domestic",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("500.00"),
            currency = "TRY",
            initiatorId = senderUser
        )

        order1.id shouldBe order2.id
        ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("4500.00")
    }

    @Test
    fun `should record outbox events for transfer`() {
        domesticTransferService.execute(
            idempotencyKey = "outbox-test",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("200.00"),
            currency = "TRY",
            initiatorId = senderUser
        )

        val outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM shared.outbox_events WHERE event_type IN ('PaymentInitiated', 'PaymentCompleted')",
            Int::class.java
        )
        // PaymentInitiated + PaymentCompleted + EntryPosted from seed + EntryPosted from transfer
        outboxCount shouldNotBe 0
    }
}

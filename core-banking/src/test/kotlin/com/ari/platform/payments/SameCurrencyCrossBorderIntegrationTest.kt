package com.ari.platform.payments

import com.ari.platform.BaseIntegrationTest
import com.ari.platform.TestConfig
import com.ari.platform.ledger.internal.model.*
import com.ari.platform.ledger.internal.repository.AccountRepository
import com.ari.platform.ledger.internal.service.LedgerService
import com.ari.platform.payments.internal.model.PaymentStatus
import com.ari.platform.payments.internal.model.PaymentType
import com.ari.platform.payments.internal.repository.PaymentOrderRepository
import com.ari.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ari.platform.payments.internal.service.SameCurrencyCrossBorderService
import com.ari.platform.shared.exception.BadRequestException
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
class SameCurrencyCrossBorderIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var sameCurrencyCrossBorderService: SameCurrencyCrossBorderService

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
    private lateinit var senderAccount: Account  // TRY in TR
    private lateinit var receiverAccount: Account  // TRY in EU
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

        val systemUser = UUID.fromString("00000000-0000-0000-0000-000000000000")
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, status, region)
            VALUES (?, 'system@ari.com', '+900000000000', 'hash', 'active', 'TR')
            ON CONFLICT (id) DO NOTHING
            """,
            systemUser
        )

        // Sender: TRY account in TR region
        senderAccount = accountRepository.save(
            Account(userId = senderUser, currency = "TRY", accountType = AccountType.USER_WALLET, region = "TR")
        )
        // Receiver: TRY account in EU region (same currency, different region)
        receiverAccount = accountRepository.save(
            Account(userId = receiverUser, currency = "TRY", accountType = AccountType.USER_WALLET, region = "EU")
        )
        systemFloat = accountRepository.save(
            Account(userId = systemUser, currency = "TRY", accountType = AccountType.SYSTEM_FLOAT, region = "TR")
        )

        // Seed sender with 10,000 TRY
        ledgerService.postEntries(
            idempotencyKey = "seed-${senderAccount.id}",
            type = TransactionType.DEPOSIT,
            postings = listOf(
                PostingInstruction(systemFloat.id, EntryDirection.DEBIT, BigDecimal("10000.00"), "TRY"),
                PostingInstruction(senderAccount.id, EntryDirection.CREDIT, BigDecimal("10000.00"), "TRY")
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
    fun `should initiate same-ccy cross-border and debit sender to transit`() {
        val order = sameCurrencyCrossBorderService.execute(
            idempotencyKey = "same-ccy-e2e-1",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("1000.00"),
            description = "Same-ccy cross-border test",
            initiatorId = senderUser
        )

        order shouldNotBe null
        order.type shouldBe PaymentType.CROSS_BORDER_SAME_CCY
        order.status shouldBe PaymentStatus.SETTLING
        order.currency shouldBe "TRY"
        order.amount shouldBeEqualComparingTo BigDecimal("1000.00")
        order.ledgerTransactionId shouldNotBe null

        // Sender debited
        ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("9000.00")
        // Receiver NOT credited (funds in transit)
        ledgerService.getBalance(receiverAccount.id) shouldBeEqualComparingTo BigDecimal("0")

        // Verify outbox event published
        val outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM shared.outbox_events WHERE event_type = 'CrossBorderBurnMintRequested'",
            Int::class.java
        )
        outboxCount shouldBe 1
    }

    @Test
    fun `should reject same-ccy transfer when currencies differ`() {
        val eurAccount = accountRepository.save(
            Account(userId = receiverUser, currency = "EUR", accountType = AccountType.USER_WALLET, region = "EU")
        )

        shouldThrow<BadRequestException> {
            sameCurrencyCrossBorderService.execute(
                idempotencyKey = "diff-ccy-reject",
                senderAccountId = senderAccount.id,
                receiverAccountId = eurAccount.id,
                amount = BigDecimal("1000.00"),
                initiatorId = senderUser
            )
        }.message shouldBe "Same-currency cross-border requires identical currencies. Use FX cross-border instead."
    }

    @Test
    fun `should reject same-ccy transfer when regions are same`() {
        val sameRegionAccount = accountRepository.save(
            Account(userId = receiverUser, currency = "TRY", accountType = AccountType.USER_WALLET, region = "TR")
        )

        shouldThrow<BadRequestException> {
            sameCurrencyCrossBorderService.execute(
                idempotencyKey = "same-region-reject",
                senderAccountId = senderAccount.id,
                receiverAccountId = sameRegionAccount.id,
                amount = BigDecimal("1000.00"),
                initiatorId = senderUser
            )
        }.message shouldBe "Same-currency cross-border requires different regions. Use domestic transfer instead."
    }

    @Test
    fun `should reject transfer from frozen account`() {
        accountRepository.updateStatus(senderAccount.id, AccountStatus.FROZEN)

        shouldThrow<BadRequestException> {
            sameCurrencyCrossBorderService.execute(
                idempotencyKey = "frozen-sender",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("100.00"),
                initiatorId = senderUser
            )
        }
    }

    @Test
    fun `should be idempotent on replay`() {
        val order1 = sameCurrencyCrossBorderService.execute(
            idempotencyKey = "idempotent-same-ccy",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("500.00"),
            initiatorId = senderUser
        )

        val order2 = sameCurrencyCrossBorderService.execute(
            idempotencyKey = "idempotent-same-ccy",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("500.00"),
            initiatorId = senderUser
        )

        order1.id shouldBe order2.id
        ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("9500.00")
    }

    @Test
    fun `should record status history transitions`() {
        val order = sameCurrencyCrossBorderService.execute(
            idempotencyKey = "status-history-test",
            senderAccountId = senderAccount.id,
            receiverAccountId = receiverAccount.id,
            amount = BigDecimal("200.00"),
            initiatorId = senderUser
        )

        val history = statusHistoryRepository.findByPaymentOrderId(order.id)
        val statuses = history.map { it.toStatus }
        statuses shouldBe listOf(
            PaymentStatus.INITIATED,
            PaymentStatus.COMPLIANCE_CHECK,
            PaymentStatus.PROCESSING,
            PaymentStatus.SETTLING
        )
    }
}

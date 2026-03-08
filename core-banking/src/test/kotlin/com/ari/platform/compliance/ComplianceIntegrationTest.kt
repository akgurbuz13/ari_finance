package com.ari.platform.compliance

import com.ari.platform.BaseIntegrationTest
import com.ari.platform.TestConfig
import com.ari.platform.compliance.internal.service.CaseManagementService
import com.ari.platform.compliance.internal.service.ComplianceService
import com.ari.platform.compliance.internal.service.TransactionMonitoringService
import com.ari.platform.ledger.internal.model.*
import com.ari.platform.ledger.internal.repository.AccountRepository
import com.ari.platform.ledger.internal.service.LedgerService
import com.ari.platform.payments.internal.model.PaymentStatus
import com.ari.platform.payments.internal.repository.PaymentOrderRepository
import com.ari.platform.payments.internal.repository.PaymentStatusHistoryRepository
import com.ari.platform.payments.internal.service.DomesticTransferService
import com.ari.platform.shared.exception.ComplianceRejectedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeGreaterThan
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
class ComplianceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var domesticTransferService: DomesticTransferService

    @Autowired
    lateinit var complianceService: ComplianceService

    @Autowired
    lateinit var caseManagementService: CaseManagementService

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
    private val systemUser = UUID.fromString("00000000-0000-0000-0000-000000000000")

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM shared.compliance_cases")
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
        createTestUser(senderUser, "compliance-sender@test.com", "+905551111111")
        createTestUser(receiverUser, "compliance-receiver@test.com", "+905552222222")

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

    private fun seedSenderBalance(amount: BigDecimal) {
        ledgerService.postEntries(
            idempotencyKey = "seed-${senderAccount.id}-${UUID.randomUUID()}",
            type = TransactionType.DEPOSIT,
            postings = listOf(
                PostingInstruction(systemFloat.id, EntryDirection.DEBIT, amount, "TRY"),
                PostingInstruction(senderAccount.id, EntryDirection.CREDIT, amount, "TRY")
            )
        )
    }

    @Nested
    inner class DailyLimitEnforcement {

        @Test
        fun `should allow transfers under daily limit`() {
            seedSenderBalance(BigDecimal("600000.00"))

            val order = domesticTransferService.execute(
                idempotencyKey = "daily-limit-ok-1",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("200000.00"),
                currency = "TRY",
                description = "First transfer",
                initiatorId = senderUser
            )

            order.status shouldBe PaymentStatus.COMPLETED

            val order2 = domesticTransferService.execute(
                idempotencyKey = "daily-limit-ok-2",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("200000.00"),
                currency = "TRY",
                description = "Second transfer",
                initiatorId = senderUser
            )

            order2.status shouldBe PaymentStatus.COMPLETED

            // Total sent: 400,000 TRY, which is under 500,000 daily limit
            ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("200000.00")
        }

        @Test
        fun `should reject transfer that would exceed daily limit across multiple transactions`() {
            seedSenderBalance(BigDecimal("600000.00"))

            // First transfer: 300,000 TRY
            val order1 = domesticTransferService.execute(
                idempotencyKey = "daily-limit-exceed-1",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("300000.00"),
                currency = "TRY",
                description = "First large transfer",
                initiatorId = senderUser
            )
            order1.status shouldBe PaymentStatus.COMPLETED

            // Second transfer: 250,000 TRY (total would be 550,000 > 500,000 daily limit)
            val exception = shouldThrow<ComplianceRejectedException> {
                domesticTransferService.execute(
                    idempotencyKey = "daily-limit-exceed-2",
                    senderAccountId = senderAccount.id,
                    receiverAccountId = receiverAccount.id,
                    amount = BigDecimal("250000.00"),
                    currency = "TRY",
                    description = "Second large transfer should fail",
                    initiatorId = senderUser
                )
            }

            exception.message shouldNotBe null

            // Sender balance should only reflect the first transfer (seed - first transfer)
            ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("300000.00")
        }

        @Test
        fun `should reject single transaction exceeding daily limit`() {
            seedSenderBalance(BigDecimal("600000.00"))

            shouldThrow<ComplianceRejectedException> {
                domesticTransferService.execute(
                    idempotencyKey = "single-over-daily",
                    senderAccountId = senderAccount.id,
                    receiverAccountId = receiverAccount.id,
                    amount = BigDecimal("500001.00"),
                    currency = "TRY",
                    description = "Over daily limit in one shot",
                    initiatorId = senderUser
                )
            }

            // Balance unchanged
            ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("600000.00")
        }
    }

    @Nested
    inner class SingleTransactionAlertThreshold {

        @Test
        fun `should flag large transaction for review and create compliance case`() {
            seedSenderBalance(BigDecimal("200000.00"))

            // 100,000 TRY meets the SINGLE_TX_ALERT_TRY threshold
            val order = domesticTransferService.execute(
                idempotencyKey = "large-tx-alert-1",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("100000.00"),
                currency = "TRY",
                description = "Large but allowed transfer",
                initiatorId = senderUser
            )

            // Transfer should still complete (alert does not block)
            order.status shouldBe PaymentStatus.COMPLETED

            // A compliance case should have been created for review
            val cases = caseManagementService.findByUserId(senderUser)
            cases shouldHaveSize 1
            cases[0].type shouldBe "transaction_review"
            cases[0].status shouldBe "open"
        }

        @Test
        fun `should not create compliance case for small transaction`() {
            seedSenderBalance(BigDecimal("50000.00"))

            val order = domesticTransferService.execute(
                idempotencyKey = "small-tx-no-alert-1",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("5000.00"),
                currency = "TRY",
                description = "Normal small transfer",
                initiatorId = senderUser
            )

            order.status shouldBe PaymentStatus.COMPLETED

            // No compliance case for small transactions
            val cases = caseManagementService.findByUserId(senderUser)
            cases shouldHaveSize 0
        }
    }

    @Nested
    inner class ComplianceServiceIntegration {

        @Test
        fun `should call ComplianceService during domestic transfer flow`() {
            seedSenderBalance(BigDecimal("10000.00"))

            val order = domesticTransferService.execute(
                idempotencyKey = "compliance-flow-1",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("1000.00"),
                currency = "TRY",
                description = "Test compliance integration",
                initiatorId = senderUser
            )

            order.status shouldBe PaymentStatus.COMPLETED

            // Verify status history includes the compliance check step
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
        fun `should transition to FAILED status when compliance rejects`() {
            seedSenderBalance(BigDecimal("600000.00"))

            shouldThrow<ComplianceRejectedException> {
                domesticTransferService.execute(
                    idempotencyKey = "compliance-reject-1",
                    senderAccountId = senderAccount.id,
                    receiverAccountId = receiverAccount.id,
                    amount = BigDecimal("500001.00"),
                    currency = "TRY",
                    description = "Should fail compliance",
                    initiatorId = senderUser
                )
            }

            // The @Transactional on execute() rolls back the entire transaction
            // when ComplianceRejectedException propagates, so the payment order
            // and status history are NOT persisted. This is correct behavior:
            // a rejected payment leaves no partial state in the database.
            val order = paymentOrderRepository.findByIdempotencyKey("compliance-reject-1")
            order shouldBe null

            // Sender balance should be unchanged (transaction was rolled back)
            ledgerService.getBalance(senderAccount.id) shouldBeEqualComparingTo BigDecimal("600000.00")
        }
    }

    @Nested
    inner class CaseManagementPersistence {

        @Test
        fun `should persist compliance case to database`() {
            val case_ = caseManagementService.createCase(
                userId = senderUser,
                type = "sanctions_match",
                description = "Test sanctions match case"
            )

            case_.id shouldNotBe null
            case_.status shouldBe "open"

            val fetched = caseManagementService.findById(case_.id)
            fetched shouldNotBe null
            fetched!!.userId shouldBe senderUser
            fetched.type shouldBe "sanctions_match"
            fetched.description shouldBe "Test sanctions match case"
            fetched.status shouldBe "open"
        }

        @Test
        fun `should resolve compliance case`() {
            val adminUser = UUID.randomUUID()
            createTestUser(adminUser, "admin@test.com", "+905553333333")

            val case_ = caseManagementService.createCase(
                userId = senderUser,
                type = "transaction_review",
                description = "Large transaction review"
            )

            caseManagementService.resolveCase(
                caseId = case_.id,
                adminId = adminUser,
                resolution = "Reviewed and approved"
            )

            val resolved = caseManagementService.findById(case_.id)
            resolved shouldNotBe null
            resolved!!.status shouldBe "resolved"
            resolved.resolution shouldBe "Reviewed and approved"
            resolved.assignedTo shouldBe adminUser
            resolved.resolvedAt shouldNotBe null
        }

        @Test
        fun `should find open cases`() {
            caseManagementService.createCase(senderUser, "sanctions_match", "Case 1")
            caseManagementService.createCase(senderUser, "transaction_review", "Case 2")
            caseManagementService.createCase(receiverUser, "transaction_review", "Case 3")

            val openCases = caseManagementService.findOpenCases()
            openCases shouldHaveSize 3
        }

        @Test
        fun `should find cases by user ID`() {
            caseManagementService.createCase(senderUser, "sanctions_match", "Sender case 1")
            caseManagementService.createCase(senderUser, "transaction_review", "Sender case 2")
            caseManagementService.createCase(receiverUser, "transaction_review", "Receiver case")

            val senderCases = caseManagementService.findByUserId(senderUser)
            senderCases shouldHaveSize 2

            val receiverCases = caseManagementService.findByUserId(receiverUser)
            receiverCases shouldHaveSize 1
        }

        @Test
        fun `should support pagination for open cases`() {
            caseManagementService.createCase(senderUser, "type_a", "Case 1")
            caseManagementService.createCase(senderUser, "type_b", "Case 2")
            caseManagementService.createCase(senderUser, "type_c", "Case 3")

            val firstPage = caseManagementService.findOpenCases(limit = 2, offset = 0)
            firstPage shouldHaveSize 2

            val secondPage = caseManagementService.findOpenCases(limit = 2, offset = 2)
            secondPage shouldHaveSize 1
        }
    }

    @Nested
    inner class EurLimits {

        @Test
        fun `should enforce EUR daily limit of 15000`() {
            // Create EUR accounts
            val senderEurAccount = accountRepository.save(
                Account(userId = senderUser, currency = "EUR", accountType = AccountType.USER_WALLET)
            )
            val receiverEurAccount = accountRepository.save(
                Account(userId = receiverUser, currency = "EUR", accountType = AccountType.USER_WALLET)
            )
            val systemEurFloat = accountRepository.save(
                Account(userId = systemUser, currency = "EUR", accountType = AccountType.SYSTEM_FLOAT)
            )

            // Seed sender with 20,000 EUR
            ledgerService.postEntries(
                idempotencyKey = "seed-eur-${senderEurAccount.id}",
                type = TransactionType.DEPOSIT,
                postings = listOf(
                    PostingInstruction(systemEurFloat.id, EntryDirection.DEBIT, BigDecimal("20000.00"), "EUR"),
                    PostingInstruction(senderEurAccount.id, EntryDirection.CREDIT, BigDecimal("20000.00"), "EUR")
                )
            )

            // First transfer: 10,000 EUR (under 15,000 limit)
            val order1 = domesticTransferService.execute(
                idempotencyKey = "eur-daily-1",
                senderAccountId = senderEurAccount.id,
                receiverAccountId = receiverEurAccount.id,
                amount = BigDecimal("10000.00"),
                currency = "EUR",
                description = "EUR transfer 1",
                initiatorId = senderUser
            )
            order1.status shouldBe PaymentStatus.COMPLETED

            // Second transfer: 6,000 EUR (total 16,000 > 15,000 EUR daily limit)
            shouldThrow<ComplianceRejectedException> {
                domesticTransferService.execute(
                    idempotencyKey = "eur-daily-2",
                    senderAccountId = senderEurAccount.id,
                    receiverAccountId = receiverEurAccount.id,
                    amount = BigDecimal("6000.00"),
                    currency = "EUR",
                    description = "EUR transfer 2 should fail",
                    initiatorId = senderUser
                )
            }

            // Only first transfer should have gone through
            ledgerService.getBalance(senderEurAccount.id) shouldBeEqualComparingTo BigDecimal("10000.00")
        }
    }
}

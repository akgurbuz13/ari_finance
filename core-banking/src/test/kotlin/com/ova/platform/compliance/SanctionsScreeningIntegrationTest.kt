package com.ova.platform.compliance

import com.ova.platform.BaseIntegrationTest
import com.ova.platform.TestConfig
import com.ova.platform.compliance.internal.service.ComplianceService
import com.ova.platform.compliance.internal.service.SanctionsScreeningService
import com.ova.platform.ledger.internal.model.*
import com.ova.platform.ledger.internal.repository.AccountRepository
import com.ova.platform.ledger.internal.service.LedgerService
import com.ova.platform.payments.internal.service.DomesticTransferService
import com.ova.platform.shared.exception.ComplianceRejectedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

@Import(TestConfig::class)
class SanctionsScreeningIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var sanctionsScreeningService: SanctionsScreeningService

    @Autowired
    lateinit var complianceService: ComplianceService

    @Autowired
    lateinit var domesticTransferService: DomesticTransferService

    @Autowired
    lateinit var ledgerService: LedgerService

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val systemUser = UUID.fromString("00000000-0000-0000-0000-000000000000")

    @BeforeEach
    fun setUp() {
        // Clean up in dependency order
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
        jdbcTemplate.execute("DELETE FROM shared.sanctions_list")

        // Seed sanctions list entries for testing
        jdbcTemplate.update(
            """
            INSERT INTO shared.sanctions_list (full_name, list_type, source, country, aliases, active)
            VALUES (?, ?, ?, ?, ?::text[], true)
            """,
            "Viktor Petroshenko", "sanctions", "eu_consolidated", "UA",
            "{\"V. Petroshenko\",\"Petroshenko Viktor\"}"
        )

        jdbcTemplate.update(
            """
            INSERT INTO shared.sanctions_list (full_name, list_type, source, country, aliases, active)
            VALUES (?, ?, ?, ?, ?::text[], true)
            """,
            "Darkov Malenko", "sanctions", "ofac", "RU",
            "{\"D. Malenko\",\"Darkov M.\"}"
        )

        jdbcTemplate.update(
            """
            INSERT INTO shared.sanctions_list (full_name, list_type, source, country, aliases, active)
            VALUES (?, ?, ?, ?, ?::text[], true)
            """,
            "Yuri Kovalenko", "pep", "local", "UA",
            "{\"Y. Kovalenko\",\"Kovalenko Yuri\"}"
        )

        jdbcTemplate.update(
            """
            INSERT INTO shared.sanctions_list (full_name, list_type, source, country, aliases, active)
            VALUES (?, ?, ?, ?, ?::text[], true)
            """,
            "Nadia Volkov", "sanctions", "un", "RU",
            "{\"N. Volkov\",\"Nadia V.\"}"
        )

        // Create system user
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, status, region)
            VALUES (?, 'system@ova.com', '+900000000000', 'hash', 'active', 'TR')
            ON CONFLICT (id) DO NOTHING
            """,
            systemUser
        )
    }

    private fun createTestUser(
        id: UUID,
        email: String,
        phone: String,
        firstName: String? = null,
        lastName: String? = null
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, first_name, last_name, status, region)
            VALUES (?, ?, ?, 'hash', ?, ?, 'active', 'TR')
            """,
            id, email, phone, firstName, lastName
        )
    }

    private fun createAccountWithBalance(userId: UUID, amount: BigDecimal): Account {
        val account = accountRepository.save(
            Account(userId = userId, currency = "TRY", accountType = AccountType.USER_WALLET)
        )
        val systemFloat = accountRepository.save(
            Account(userId = systemUser, currency = "TRY", accountType = AccountType.SYSTEM_FLOAT)
        )
        ledgerService.postEntries(
            idempotencyKey = "seed-${account.id}-${UUID.randomUUID()}",
            type = TransactionType.DEPOSIT,
            postings = listOf(
                PostingInstruction(systemFloat.id, EntryDirection.DEBIT, amount, "TRY"),
                PostingInstruction(account.id, EntryDirection.CREDIT, amount, "TRY")
            )
        )
        return account
    }

    @Test
    fun `should pass screening for user with no sanctions match`() {
        val userId = UUID.randomUUID()
        createTestUser(userId, "clean-user@test.com", "+905551110000", "John", "Smith")

        val result = sanctionsScreeningService.screenUser(userId, "John", "Smith")

        result.passed shouldBe true
        result.matchType shouldBe null
        result.matchDetails shouldBe null
        result.matchScore shouldBe null
    }

    @Test
    fun `should block user matching sanctions list`() {
        val userId = UUID.randomUUID()
        createTestUser(userId, "sanctioned@test.com", "+905551110001", "Viktor", "Petroshenko")

        val result = sanctionsScreeningService.screenUser(userId, "Viktor", "Petroshenko")

        result.passed shouldBe false
        result.matchType shouldBe "sanctions_hit"
        result.matchDetails.shouldNotBeNull()
        result.matchDetails!! shouldContain "Viktor Petroshenko"
        result.matchDetails!! shouldContain "eu_consolidated"
        result.matchScore.shouldNotBeNull()
        result.matchScore!! shouldBeGreaterThan 0.7
    }

    @Test
    fun `should flag fuzzy match for review`() {
        val userId = UUID.randomUUID()
        // "Viktor Petrosheko" is similar but not identical to "Viktor Petroshenko"
        createTestUser(userId, "fuzzy@test.com", "+905551110002", "Viktor", "Petrosheko")

        val result = sanctionsScreeningService.screenUser(userId, "Viktor", "Petrosheko")

        // With trigram similarity, a close misspelling should produce a score between 0.4 and 0.7
        // or above 0.7 depending on pg_trgm -- the key assertion is that it flags it
        if (result.matchScore != null && result.matchScore!! >= 0.7) {
            // If the similarity is high enough to block, that is also acceptable
            result.passed shouldBe false
            result.matchType shouldBe "sanctions_hit"
        } else if (result.matchScore != null && result.matchScore!! >= 0.4) {
            result.passed shouldBe true
            result.matchType shouldBe "possible_match"
            result.matchDetails.shouldNotBeNull()
            result.matchDetails!! shouldContain "Possible match"
        }
        // In either case, the match score should be non-null for a close name
        result.matchScore shouldNotBe null
    }

    @Test
    fun `should screen transaction and block if sender matches`() {
        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()
        createTestUser(senderId, "darkov-sender@test.com", "+905551110003", "Darkov", "Malenko")
        createTestUser(receiverId, "clean-receiver@test.com", "+905551110004", "Jane", "Doe")

        val senderAccount = createAccountWithBalance(senderId, BigDecimal("10000.00"))
        val receiverAccount = accountRepository.save(
            Account(userId = receiverId, currency = "TRY", accountType = AccountType.USER_WALLET)
        )

        shouldThrow<ComplianceRejectedException> {
            domesticTransferService.execute(
                idempotencyKey = "sanctions-block-sender-${UUID.randomUUID()}",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("1000.00"),
                currency = "TRY",
                description = "Transfer from sanctioned sender",
                initiatorId = senderId
            )
        }
    }

    @Test
    fun `should screen transaction and block if receiver matches`() {
        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()
        createTestUser(senderId, "clean-sender@test.com", "+905551110005", "Alice", "Johnson")
        createTestUser(receiverId, "nadia-receiver@test.com", "+905551110006", "Nadia", "Volkov")

        val senderAccount = createAccountWithBalance(senderId, BigDecimal("10000.00"))
        val receiverAccount = accountRepository.save(
            Account(userId = receiverId, currency = "TRY", accountType = AccountType.USER_WALLET)
        )

        shouldThrow<ComplianceRejectedException> {
            domesticTransferService.execute(
                idempotencyKey = "sanctions-block-receiver-${UUID.randomUUID()}",
                senderAccountId = senderAccount.id,
                receiverAccountId = receiverAccount.id,
                amount = BigDecimal("1000.00"),
                currency = "TRY",
                description = "Transfer to sanctioned receiver",
                initiatorId = senderId
            )
        }
    }

    @Test
    fun `should pass screening when name is null`() {
        val userId = UUID.randomUUID()
        createTestUser(userId, "noname@test.com", "+905551110007")

        val result = sanctionsScreeningService.screenUser(userId, null, null)

        result.passed shouldBe true
        result.matchType shouldBe null
    }

    @Test
    fun `should match against aliases`() {
        val userId = UUID.randomUUID()
        // "Kovalenko Yuri" is an alias for "Yuri Kovalenko" in the PEP list
        createTestUser(userId, "alias-match@test.com", "+905551110008", "Kovalenko", "Yuri")

        val result = sanctionsScreeningService.screenUser(userId, "Kovalenko", "Yuri")

        // The alias "Kovalenko Yuri" should produce a high similarity score
        // against the input "Kovalenko Yuri"
        result.matchScore shouldNotBe null
        result.matchScore.shouldNotBeNull()

        // The alias is an exact match, so it should block or at least flag
        if (result.matchScore!! >= 0.7) {
            result.passed shouldBe false
            result.matchType shouldBe "pep_match"
        } else if (result.matchScore!! >= 0.4) {
            result.passed shouldBe true
            result.matchType shouldBe "possible_match"
        }
    }
}

package com.ova.platform.identity

import com.fasterxml.jackson.databind.ObjectMapper
import com.ova.platform.BaseIntegrationTest
import com.ova.platform.TestConfig
import com.ova.platform.identity.internal.model.KycLevel
import com.ova.platform.identity.internal.model.KycStatus
import com.ova.platform.identity.internal.repository.KycRepository
import com.ova.platform.identity.internal.service.KycService
import com.ova.platform.shared.exception.BadRequestException
import com.ova.platform.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@Import(TestConfig::class)
class KycProviderIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var kycService: KycService

    @Autowired
    lateinit var kycRepository: KycRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setUp() {
        // Clean data in reverse dependency order
        jdbcTemplate.execute("DELETE FROM shared.outbox_events")
        jdbcTemplate.execute("DELETE FROM identity.kyc_verifications")
        jdbcTemplate.execute("DELETE FROM shared.audit_log")
        jdbcTemplate.execute("DELETE FROM identity.refresh_tokens")
        jdbcTemplate.execute("DELETE FROM identity.users")

        // Create a test user
        testUserId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO identity.users (id, email, phone, password_hash, first_name, last_name, status, region)
            VALUES (?, ?, ?, 'hash', 'John', 'Doe', 'pending_kyc', 'TR')
            """,
            testUserId, "kyc-test-${testUserId}@test.com", "+9055500${(10000..99999).random()}"
        )
    }

    @Nested
    inner class InitiateVerification {

        @Test
        fun `should initiate KYC verification via provider and get session URL`() {
            val result = kycService.initiateVerification(testUserId, "simulated", KycLevel.BASIC)

            result.verificationId.shouldNotBeNull()
            result.sessionUrl.shouldNotBeNull()
            result.sessionUrl!! shouldStartWith "https://verify.ova.dev/session/"
            result.sessionToken.shouldNotBeNull()
            result.expiresInSeconds shouldBe 1800

            // Verify KYC record created in PENDING status
            val kyc = kycRepository.findById(result.verificationId)
            kyc.shouldNotBeNull()
            kyc.status shouldBe KycStatus.PENDING
            kyc.provider shouldBe "simulated"
            kyc.level shouldBe KycLevel.BASIC
            kyc.userId shouldBe testUserId
        }

        @Test
        fun `should reject duplicate initiation when pending exists`() {
            kycService.initiateVerification(testUserId, "simulated", KycLevel.BASIC)

            shouldThrow<BadRequestException> {
                kycService.initiateVerification(testUserId, "simulated", KycLevel.BASIC)
            }.message shouldBe "KYC verification already in progress"
        }

        @Test
        fun `should reject unknown provider`() {
            shouldThrow<BadRequestException> {
                kycService.initiateVerification(testUserId, "unknown_provider", KycLevel.BASIC)
            }.message shouldBe "Unknown KYC provider: unknown_provider"
        }
    }

    @Nested
    inner class WebhookCallback {

        @Test
        fun `should handle approval webhook and update status`() {
            val result = kycService.initiateVerification(testUserId, "simulated", KycLevel.BASIC)

            // Find the providerRef from the saved KYC record
            val kyc = kycRepository.findById(result.verificationId)!!
            val providerRef = kyc.providerRef

            // Simulate approval webhook
            val webhookPayload = objectMapper.writeValueAsString(
                mapOf("providerRef" to providerRef, "status" to "approved")
            )
            kycService.handleWebhookCallback("simulated", webhookPayload, null)

            // Verify KYC updated to APPROVED
            val updatedKyc = kycRepository.findById(result.verificationId)!!
            updatedKyc.status shouldBe KycStatus.APPROVED

            // Verify user status becomes ACTIVE
            val userStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM identity.users WHERE id = ?",
                String::class.java, testUserId
            )
            userStatus shouldBe "active"

            // Verify KycApproved event in outbox
            val outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_events WHERE event_type = 'KycApproved' AND aggregate_id = ?",
                Int::class.java, result.verificationId.toString()
            )
            outboxCount shouldBe 1
        }

        @Test
        fun `should handle rejection webhook and update status`() {
            val result = kycService.initiateVerification(testUserId, "simulated", KycLevel.BASIC)
            val kyc = kycRepository.findById(result.verificationId)!!
            val providerRef = kyc.providerRef

            // Simulate rejection webhook
            val webhookPayload = objectMapper.writeValueAsString(
                mapOf(
                    "providerRef" to providerRef,
                    "status" to "rejected",
                    "reason" to "Document not readable"
                )
            )
            kycService.handleWebhookCallback("simulated", webhookPayload, null)

            // Verify KYC updated to REJECTED
            val updatedKyc = kycRepository.findById(result.verificationId)!!
            updatedKyc.status shouldBe KycStatus.REJECTED
            updatedKyc.rejectionReason shouldBe "Document not readable"

            // Verify user status remains pending_kyc (not activated)
            val userStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM identity.users WHERE id = ?",
                String::class.java, testUserId
            )
            userStatus shouldBe "pending_kyc"

            // Verify KycRejected event in outbox
            val outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_events WHERE event_type = 'KycRejected' AND aggregate_id = ?",
                Int::class.java, result.verificationId.toString()
            )
            outboxCount shouldBe 1
        }

        @Test
        fun `should ignore webhook for already processed verification`() {
            val result = kycService.initiateVerification(testUserId, "simulated", KycLevel.BASIC)
            val kyc = kycRepository.findById(result.verificationId)!!
            val providerRef = kyc.providerRef

            // First webhook - approve
            val approvalPayload = objectMapper.writeValueAsString(
                mapOf("providerRef" to providerRef, "status" to "approved")
            )
            kycService.handleWebhookCallback("simulated", approvalPayload, null)

            // Second webhook - should be ignored without error
            shouldNotThrowAny {
                kycService.handleWebhookCallback("simulated", approvalPayload, null)
            }

            // Verify still APPROVED (no duplicate processing)
            val updatedKyc = kycRepository.findById(result.verificationId)!!
            updatedKyc.status shouldBe KycStatus.APPROVED

            // Verify only one outbox event
            val outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.outbox_events WHERE event_type = 'KycApproved' AND aggregate_id = ?",
                Int::class.java, result.verificationId.toString()
            )
            outboxCount shouldBe 1
        }

        @Test
        fun `should throw for unknown providerRef in webhook`() {
            val webhookPayload = objectMapper.writeValueAsString(
                mapOf("providerRef" to UUID.randomUUID().toString(), "status" to "approved")
            )

            shouldThrow<NotFoundException> {
                kycService.handleWebhookCallback("simulated", webhookPayload, null)
            }.message shouldContain "providerRef="
        }
    }

    @Nested
    inner class GetKycById {

        @Test
        fun `should get verification session status`() {
            val result = kycService.initiateVerification(testUserId, "simulated", KycLevel.ENHANCED)

            val kyc = kycService.getKycById(result.verificationId)
            kyc.shouldNotBeNull()
            kyc.id shouldBe result.verificationId
            kyc.status shouldBe KycStatus.PENDING
            kyc.level shouldBe KycLevel.ENHANCED
            kyc.provider shouldBe "simulated"
        }

        @Test
        fun `should throw for non-existent verification ID`() {
            val fakeId = UUID.randomUUID()
            shouldThrow<NotFoundException> {
                kycService.getKycById(fakeId)
            }.message shouldContain fakeId.toString()
        }
    }
}

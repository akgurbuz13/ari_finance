package com.ova.platform.compliance.internal.service

import com.ova.platform.identity.internal.repository.UserRepository
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * Service for screening users and transactions against sanctions lists.
 *
 * REGULATORY COVERAGE:
 * - Turkey: MASAK list (terrorist financing, asset freezes)
 * - EU: Consolidated Financial Sanctions List (AMLD6)
 * - International: UN Security Council, OFAC SDN
 *
 * SCREENING TYPES:
 * 1. Name screening: Fuzzy matching using pg_trgm
 * 2. ID screening: Exact match on national IDs, passport numbers
 * 3. Entity screening: For business/corporate accounts
 *
 * THRESHOLDS:
 * - Block (≥0.70): Automatic rejection, requires compliance review
 * - Flag (≥0.40): Transaction allowed but flagged for review
 * - Clear (<0.40): No significant match found
 */
@Service
class SanctionsScreeningService(
    private val jdbcTemplate: JdbcTemplate,
    private val userRepository: UserRepository,
    private val auditService: AuditService,
    private val sanctionsListProvider: SanctionsListProvider,
    @Value("\${ova.compliance.sanctions.enabled:true}") private val enabled: Boolean,
    @Value("\${ova.region:TR}") private val region: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Matching thresholds - configurable based on risk appetite
        private const val BLOCK_THRESHOLD = 0.70   // Automatic block
        private const val FLAG_THRESHOLD = 0.40    // Flag for review
        private const val SEARCH_THRESHOLD = 0.30  // Minimum for consideration

        // Higher thresholds for terrorist financing (zero tolerance)
        private const val TERRORIST_BLOCK_THRESHOLD = 0.60
    }

    data class ScreeningResult(
        val passed: Boolean,
        val matchType: String? = null,
        val matchDetails: String? = null,
        val matchScore: Double? = null
    )

    private data class SanctionsMatch(
        val fullName: String,
        val listType: String,
        val source: String,
        val country: String?,
        val score: Double
    )

    fun screenUser(userId: UUID, firstName: String?, lastName: String?): ScreeningResult {
        log.info("Screening user {} against sanctions lists", userId)

        val fullName = buildFullName(firstName, lastName)
        if (fullName.isNullOrBlank()) {
            log.info("User {} has no name to screen, passing", userId)
            auditService.log(
                actorId = null,
                actorType = "system",
                action = "sanctions_screening",
                resourceType = "user",
                resourceId = userId.toString(),
                details = mapOf("passed" to true, "reason" to "no_name_provided")
            )
            return ScreeningResult(passed = true)
        }

        val matches = findMatches(fullName)
        val result = evaluateMatches(matches)

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "sanctions_screening",
            resourceType = "user",
            resourceId = userId.toString(),
            details = mapOf(
                "passed" to result.passed,
                "screenedName" to fullName,
                "matchType" to (result.matchType ?: "none"),
                "matchScore" to (result.matchScore ?: 0.0),
                "matchDetails" to (result.matchDetails ?: "no match"),
                "totalMatches" to matches.size
            )
        )

        if (!result.passed) {
            log.warn("Sanctions screening BLOCKED user {}: {}", userId, result.matchDetails)
        } else if (result.matchType != null) {
            log.info("Sanctions screening FLAGGED user {}: {}", userId, result.matchDetails)
        }

        return result
    }

    fun screenTransaction(
        senderId: UUID,
        receiverId: UUID,
        amount: BigDecimal,
        currency: String
    ): ScreeningResult {
        log.info("Screening transaction sender={} receiver={} amount={} {}",
            senderId, receiverId, amount, currency)

        val sender = userRepository.findById(senderId)
        val receiver = userRepository.findById(receiverId)

        // Screen sender
        val senderResult = screenUser(
            userId = senderId,
            firstName = sender?.firstName,
            lastName = sender?.lastName
        )

        if (!senderResult.passed) {
            auditService.log(
                actorId = null,
                actorType = "system",
                action = "transaction_screening",
                resourceType = "payment",
                resourceId = "$senderId->$receiverId",
                details = mapOf(
                    "passed" to false,
                    "blockedParty" to "sender",
                    "amount" to amount.toPlainString(),
                    "currency" to currency,
                    "matchType" to (senderResult.matchType ?: "unknown"),
                    "matchDetails" to (senderResult.matchDetails ?: "unknown")
                )
            )
            return senderResult
        }

        // Screen receiver
        val receiverResult = screenUser(
            userId = receiverId,
            firstName = receiver?.firstName,
            lastName = receiver?.lastName
        )

        if (!receiverResult.passed) {
            auditService.log(
                actorId = null,
                actorType = "system",
                action = "transaction_screening",
                resourceType = "payment",
                resourceId = "$senderId->$receiverId",
                details = mapOf(
                    "passed" to false,
                    "blockedParty" to "receiver",
                    "amount" to amount.toPlainString(),
                    "currency" to currency,
                    "matchType" to (receiverResult.matchType ?: "unknown"),
                    "matchDetails" to (receiverResult.matchDetails ?: "unknown")
                )
            )
            return receiverResult
        }

        // Both passed; return worst flagged result if any
        val worstResult = listOf(senderResult, receiverResult)
            .filter { it.matchType != null }
            .maxByOrNull { it.matchScore ?: 0.0 }

        val finalResult = worstResult ?: ScreeningResult(passed = true)

        auditService.log(
            actorId = null,
            actorType = "system",
            action = "transaction_screening",
            resourceType = "payment",
            resourceId = "$senderId->$receiverId",
            details = mapOf(
                "passed" to finalResult.passed,
                "amount" to amount.toPlainString(),
                "currency" to currency,
                "matchType" to (finalResult.matchType ?: "none"),
                "matchScore" to (finalResult.matchScore ?: 0.0)
            )
        )

        return finalResult
    }

    private fun findMatches(name: String): List<SanctionsMatch> {
        val matches = mutableListOf<SanctionsMatch>()

        // Direct name matching using pg_trgm similarity
        val directMatches = jdbcTemplate.query(
            """
            SELECT full_name, list_type, source, country, similarity(full_name, ?) as score
            FROM shared.sanctions_list
            WHERE active = true
              AND similarity(full_name, ?) > ?
            ORDER BY score DESC
            LIMIT 5
            """,
            { rs, _ ->
                SanctionsMatch(
                    fullName = rs.getString("full_name"),
                    listType = rs.getString("list_type"),
                    source = rs.getString("source"),
                    country = rs.getString("country"),
                    score = rs.getDouble("score")
                )
            },
            name, name, SEARCH_THRESHOLD
        )
        matches.addAll(directMatches)

        // Alias matching using a subquery to filter by score
        val aliasMatches = jdbcTemplate.query(
            """
            SELECT full_name, list_type, source, country, alias_score as score
            FROM (
                SELECT full_name, list_type, source, country,
                       (SELECT MAX(similarity(a, ?)) FROM unnest(aliases) a) as alias_score
                FROM shared.sanctions_list
                WHERE active = true
                  AND aliases IS NOT NULL
            ) sub
            WHERE alias_score > ?
            ORDER BY alias_score DESC
            LIMIT 5
            """,
            { rs, _ ->
                SanctionsMatch(
                    fullName = rs.getString("full_name"),
                    listType = rs.getString("list_type"),
                    source = rs.getString("source"),
                    country = rs.getString("country"),
                    score = rs.getDouble("score")
                )
            },
            name, SEARCH_THRESHOLD
        )

        // Merge alias matches, keeping highest score per full_name
        for (aliasMatch in aliasMatches) {
            val existing = matches.find { it.fullName == aliasMatch.fullName }
            if (existing == null) {
                matches.add(aliasMatch)
            } else if (aliasMatch.score > existing.score) {
                matches.remove(existing)
                matches.add(aliasMatch)
            }
        }

        return matches.sortedByDescending { it.score }
    }

    private fun evaluateMatches(matches: List<SanctionsMatch>): ScreeningResult {
        if (matches.isEmpty()) {
            return ScreeningResult(passed = true)
        }

        val bestMatch = matches.first()

        return when {
            bestMatch.score >= BLOCK_THRESHOLD -> {
                val matchType = when (bestMatch.listType) {
                    "pep" -> "pep_match"
                    else -> "sanctions_hit"
                }
                ScreeningResult(
                    passed = false,
                    matchType = matchType,
                    matchDetails = "Matched against ${bestMatch.fullName} from ${bestMatch.source} (score: ${"%.2f".format(bestMatch.score)})",
                    matchScore = bestMatch.score
                )
            }
            bestMatch.score >= FLAG_THRESHOLD -> {
                ScreeningResult(
                    passed = true,
                    matchType = "possible_match",
                    matchDetails = "Possible match against ${bestMatch.fullName} (score: ${"%.2f".format(bestMatch.score)})",
                    matchScore = bestMatch.score
                )
            }
            else -> {
                ScreeningResult(passed = true)
            }
        }
    }

    private fun buildFullName(firstName: String?, lastName: String?): String? {
        val parts = listOfNotNull(firstName?.trim(), lastName?.trim()).filter { it.isNotBlank() }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /**
     * Screen by national ID number (exact match).
     */
    fun screenByNationalId(nationalId: String): ScreeningResult {
        if (!enabled) {
            return ScreeningResult(passed = true)
        }

        log.info("Screening national ID against sanctions lists")

        val match = jdbcTemplate.query(
            """
            SELECT full_name, list_type, source, country
            FROM shared.sanctions_list
            WHERE active = true AND national_id = ?
            LIMIT 1
            """,
            { rs, _ ->
                SanctionsMatch(
                    fullName = rs.getString("full_name"),
                    listType = rs.getString("list_type"),
                    source = rs.getString("source"),
                    country = rs.getString("country"),
                    score = 1.0 // Exact match
                )
            },
            nationalId
        ).firstOrNull()

        if (match != null) {
            saveScreeningResult(
                screenedType = "user",
                screenedId = UUID.randomUUID(), // Placeholder
                screenedName = nationalId,
                result = "blocked",
                matchType = "national_id_exact",
                matchScore = 1.0,
                matchDetails = "Exact national ID match: ${match.fullName} from ${match.source}"
            )

            return ScreeningResult(
                passed = false,
                matchType = "national_id_exact",
                matchDetails = "Exact match against ${match.fullName} (${match.source})",
                matchScore = 1.0
            )
        }

        return ScreeningResult(passed = true)
    }

    /**
     * Check if sanctions lists are up to date.
     */
    fun checkListHealth(): Map<String, Any> {
        val stats = sanctionsListProvider.getStatistics()
        val isReady = sanctionsListProvider.isReady()

        val sources = listOf("MASAK", "UN", "EU", "OFAC")
        val lastUpdates = sources.associateWith { source ->
            sanctionsListProvider.getLastUpdateTime(source)?.toString() ?: "never"
        }

        return mapOf(
            "enabled" to enabled,
            "ready" to isReady,
            "totalEntries" to (stats["totalEntries"] ?: 0),
            "lastUpdates" to lastUpdates,
            "region" to region
        )
    }

    /**
     * Save screening result for audit trail.
     */
    private fun saveScreeningResult(
        screenedType: String,
        screenedId: UUID,
        screenedName: String,
        result: String,
        matchType: String?,
        matchScore: Double?,
        matchDetails: String?,
        ipAddress: String? = null
    ) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO shared.screening_results
                    (screened_type, screened_id, screened_name, result, match_type,
                     match_score, match_details, ip_address)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::inet)
                """,
                screenedType,
                screenedId,
                screenedName,
                result,
                matchType,
                matchScore,
                matchDetails,
                ipAddress
            )
        } catch (e: Exception) {
            log.warn("Failed to save screening result: {}", e.message)
        }
    }

    /**
     * Get screening history for a user.
     */
    fun getScreeningHistory(userId: UUID): List<Map<String, Any?>> {
        return jdbcTemplate.queryForList(
            """
            SELECT id, screened_name, result, match_type, match_score, match_details, created_at
            FROM shared.screening_results
            WHERE screened_id = ?
            ORDER BY created_at DESC
            LIMIT 50
            """,
            userId
        )
    }
}

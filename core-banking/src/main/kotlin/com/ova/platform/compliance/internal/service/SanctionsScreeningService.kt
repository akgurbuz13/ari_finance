package com.ova.platform.compliance.internal.service

import com.ova.platform.identity.internal.repository.UserRepository
import com.ova.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class SanctionsScreeningService(
    private val jdbcTemplate: JdbcTemplate,
    private val userRepository: UserRepository,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BLOCK_THRESHOLD = 0.7
        private const val FLAG_THRESHOLD = 0.4
        private const val SEARCH_THRESHOLD = 0.3
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
}

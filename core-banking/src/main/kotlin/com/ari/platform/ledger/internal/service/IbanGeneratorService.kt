package com.ari.platform.ledger.internal.service

import com.ari.platform.shared.config.RegionConfig
import com.ari.platform.shared.model.Region
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.util.UUID

/**
 * Generates IBANs for user wallet accounts.
 *
 * Turkey: TR + 2 check digits + 5-digit bank code + 1 reserve + 16-digit account number = 26 chars
 * Lithuania (EU): LT + 2 check digits + 5-digit bank code + 11-digit account number = 20 chars
 *
 * Bank codes are placeholders until real EMI licenses are obtained:
 * - TR: 00901 (placeholder ARI bank code for Turkey)
 * - LT: 09000 (placeholder ARI bank code for Lithuania)
 */
@Service
class IbanGeneratorService(
    private val regionConfig: RegionConfig,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TR_COUNTRY_CODE = "TR"
        const val TR_BANK_CODE = "00901"
        const val TR_RESERVE = "0"

        const val LT_COUNTRY_CODE = "LT"
        const val LT_BANK_CODE = "09000"
    }

    /**
     * Generate a unique IBAN for the given region and currency.
     */
    fun generateIban(region: Region): String {
        return when (region) {
            Region.TR -> generateTurkishIban()
            Region.EU -> generateLithuanianIban()
        }
    }

    /**
     * Generate IBAN using the configured region of this deployment.
     */
    fun generateIban(): String = generateIban(regionConfig.region)

    /**
     * Turkish IBAN: TR + 2 check digits + 5 bank code + 1 reserve + 16 account number = 26 chars
     */
    private fun generateTurkishIban(): String {
        val accountNumber = generateUniqueAccountNumber(16)
        val bban = "$TR_BANK_CODE$TR_RESERVE$accountNumber"
        val checkDigits = calculateCheckDigits(TR_COUNTRY_CODE, bban)
        return "$TR_COUNTRY_CODE$checkDigits$bban"
    }

    /**
     * Lithuanian IBAN: LT + 2 check digits + 5 bank code + 11 account number = 20 chars
     */
    private fun generateLithuanianIban(): String {
        val accountNumber = generateUniqueAccountNumber(11)
        val bban = "$LT_BANK_CODE$accountNumber"
        val checkDigits = calculateCheckDigits(LT_COUNTRY_CODE, bban)
        return "$LT_COUNTRY_CODE$checkDigits$bban"
    }

    /**
     * Calculate IBAN check digits using ISO 7064 MOD 97-10 algorithm.
     *
     * 1. Move country code + "00" to end of BBAN
     * 2. Replace letters with numbers (A=10, B=11, ..., Z=35)
     * 3. Compute mod 97 of the resulting number
     * 4. Check digits = 98 - remainder
     */
    private fun calculateCheckDigits(countryCode: String, bban: String): String {
        val rearranged = "$bban${countryCode}00"

        val numericString = rearranged.map { ch ->
            if (ch.isLetter()) {
                (ch.uppercaseChar() - 'A' + 10).toString()
            } else {
                ch.toString()
            }
        }.joinToString("")

        val remainder = BigInteger(numericString).mod(BigInteger.valueOf(97))
        val checkDigit = 98 - remainder.toInt()
        return checkDigit.toString().padStart(2, '0')
    }

    /**
     * Generate a unique numeric account number of the specified length.
     * Uses a sequence-based approach with collision detection.
     */
    private fun generateUniqueAccountNumber(length: Int): String {
        // Use UUID hash as seed, then check uniqueness
        repeat(10) {
            val uuid = UUID.randomUUID()
            val hash = uuid.mostSignificantBits.toULong()
            val number = (hash % Math.pow(10.0, length.toDouble()).toLong().toULong()).toString()
                .padStart(length, '0')

            // Ensure no existing account uses this number as part of its IBAN
            val exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger.accounts WHERE iban LIKE ?",
                Long::class.java,
                "%$number"
            ) ?: 0L

            if (exists == 0L) {
                return number
            }
        }

        throw IllegalStateException("Failed to generate unique account number after 10 attempts")
    }

    /**
     * Validate an IBAN using MOD 97-10 check.
     */
    fun validateIban(iban: String): Boolean {
        if (iban.length < 5) return false

        val rearranged = iban.substring(4) + iban.substring(0, 4)
        val numericString = rearranged.map { ch ->
            if (ch.isLetter()) {
                (ch.uppercaseChar() - 'A' + 10).toString()
            } else {
                ch.toString()
            }
        }.joinToString("")

        return try {
            BigInteger(numericString).mod(BigInteger.valueOf(97)) == BigInteger.ONE
        } catch (e: NumberFormatException) {
            false
        }
    }
}

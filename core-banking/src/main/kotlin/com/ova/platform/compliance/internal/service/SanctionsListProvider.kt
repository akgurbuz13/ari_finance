package com.ova.platform.compliance.internal.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Service for fetching and maintaining sanctions lists from various sources.
 *
 * REGULATORY REQUIREMENTS:
 * - Turkey (TCMB): MASAK (Mali Suçları Araştırma Kurulu) list
 * - EU: Consolidated Financial Sanctions List
 * - International: UN Security Council, OFAC SDN
 *
 * UPDATE FREQUENCY:
 * - MASAK: Updated as changes occur (check daily)
 * - UN: Updated as resolutions are adopted
 * - EU: Updated within 24 hours of UN changes
 * - OFAC: Updated as designations occur
 *
 * This service downloads lists and syncs them to the local database for
 * fast screening. Lists are cached and updated on a schedule.
 */
@Service
class SanctionsListProvider(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${ova.compliance.sanctions.masak-url:}") private val masakUrl: String,
    @Value("\${ova.compliance.sanctions.un-url:https://scsanctions.un.org/resources/xml/en/consolidated.xml}") private val unUrl: String,
    @Value("\${ova.compliance.sanctions.eu-url:https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content}") private val euUrl: String,
    @Value("\${ova.compliance.sanctions.ofac-url:https://www.treasury.gov/ofac/downloads/sdn.xml}") private val ofacUrl: String,
    @Value("\${ova.compliance.sanctions.enabled:true}") private val enabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    // Track last update times
    private val lastUpdateTimes = AtomicReference<Map<String, Instant>>(emptyMap())

    data class SanctionsEntry(
        val fullName: String,
        val aliases: List<String>,
        val listType: String,  // sanctions, pep, terrorist
        val source: String,    // MASAK, UN, EU, OFAC
        val country: String?,
        val dateOfBirth: String?,
        val nationalId: String?,
        val remarks: String?,
        val listingDate: Instant?,
        val externalId: String?
    )

    /**
     * Get the last update time for a specific source.
     */
    fun getLastUpdateTime(source: String): Instant? {
        return lastUpdateTimes.get()[source]
    }

    /**
     * Check if all lists are loaded.
     */
    fun isReady(): Boolean {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM shared.sanctions_list WHERE active = true",
            Int::class.java
        ) ?: 0
        return count > 0
    }

    /**
     * Get statistics about loaded sanctions lists.
     */
    fun getStatistics(): Map<String, Any> {
        val stats = jdbcTemplate.queryForList(
            """
            SELECT source, COUNT(*) as count, MAX(updated_at) as last_updated
            FROM shared.sanctions_list
            WHERE active = true
            GROUP BY source
            """
        )

        return mapOf(
            "totalEntries" to (stats.sumOf { (it["count"] as Long) }),
            "bySource" to stats.associate { it["source"] to it["count"] },
            "lastUpdates" to lastUpdateTimes.get()
        )
    }

    /**
     * Scheduled task to update all sanctions lists.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    fun updateAllLists() {
        if (!enabled) {
            log.info("Sanctions list updates are disabled")
            return
        }

        log.info("Starting scheduled sanctions list update")

        try {
            updateMasakList()
        } catch (e: Exception) {
            log.error("Failed to update MASAK list: {}", e.message)
        }

        try {
            updateUnList()
        } catch (e: Exception) {
            log.error("Failed to update UN list: {}", e.message)
        }

        try {
            updateEuList()
        } catch (e: Exception) {
            log.error("Failed to update EU list: {}", e.message)
        }

        try {
            updateOfacList()
        } catch (e: Exception) {
            log.error("Failed to update OFAC list: {}", e.message)
        }

        log.info("Sanctions list update completed")
    }

    /**
     * Update MASAK (Turkey Financial Crimes Investigation Board) list.
     *
     * MASAK maintains lists of:
     * - Terrorist organizations and individuals
     * - Money laundering suspects
     * - Asset freeze orders
     */
    fun updateMasakList() {
        log.info("Updating MASAK sanctions list")

        if (masakUrl.isBlank()) {
            log.warn("MASAK URL not configured, skipping update")
            return
        }

        try {
            // MASAK provides XML format
            val response = restTemplate.getForObject(masakUrl, String::class.java)
            val entries = parseMasakXml(response ?: "")

            syncEntriesToDatabase(entries, "MASAK")
            recordUpdateTime("MASAK")

            log.info("Updated {} entries from MASAK", entries.size)
        } catch (e: Exception) {
            log.error("Failed to fetch MASAK list: {}", e.message, e)
            throw e
        }
    }

    /**
     * Update UN Security Council Consolidated List.
     *
     * This list includes individuals and entities subject to:
     * - Asset freeze
     * - Travel ban
     * - Arms embargo
     */
    fun updateUnList() {
        log.info("Updating UN sanctions list")

        if (unUrl.isBlank()) {
            log.warn("UN URL not configured, skipping update")
            return
        }

        try {
            val response = restTemplate.getForObject(unUrl, String::class.java)
            val entries = parseUnXml(response ?: "")

            syncEntriesToDatabase(entries, "UN")
            recordUpdateTime("UN")

            log.info("Updated {} entries from UN", entries.size)
        } catch (e: Exception) {
            log.error("Failed to fetch UN list: {}", e.message, e)
            throw e
        }
    }

    /**
     * Update EU Consolidated Financial Sanctions List.
     */
    fun updateEuList() {
        log.info("Updating EU sanctions list")

        if (euUrl.isBlank()) {
            log.warn("EU URL not configured, skipping update")
            return
        }

        try {
            val response = restTemplate.getForObject(euUrl, String::class.java)
            val entries = parseEuXml(response ?: "")

            syncEntriesToDatabase(entries, "EU")
            recordUpdateTime("EU")

            log.info("Updated {} entries from EU", entries.size)
        } catch (e: Exception) {
            log.error("Failed to fetch EU list: {}", e.message, e)
            throw e
        }
    }

    /**
     * Update OFAC Specially Designated Nationals (SDN) List.
     */
    fun updateOfacList() {
        log.info("Updating OFAC SDN list")

        if (ofacUrl.isBlank()) {
            log.warn("OFAC URL not configured, skipping update")
            return
        }

        try {
            val response = restTemplate.getForObject(ofacUrl, String::class.java)
            val entries = parseOfacXml(response ?: "")

            syncEntriesToDatabase(entries, "OFAC")
            recordUpdateTime("OFAC")

            log.info("Updated {} entries from OFAC", entries.size)
        } catch (e: Exception) {
            log.error("Failed to fetch OFAC list: {}", e.message, e)
            throw e
        }
    }

    /**
     * Sync entries to the database, handling updates and deletes.
     */
    private fun syncEntriesToDatabase(entries: List<SanctionsEntry>, source: String) {
        log.debug("Syncing {} entries from {} to database", entries.size, source)

        // Mark all existing entries from this source as inactive
        jdbcTemplate.update(
            "UPDATE shared.sanctions_list SET active = false WHERE source = ?",
            source
        )

        // Upsert new entries
        for (entry in entries) {
            jdbcTemplate.update(
                """
                INSERT INTO shared.sanctions_list
                    (full_name, aliases, list_type, source, country, date_of_birth,
                     national_id, remarks, listing_date, external_id, active, updated_at)
                VALUES (?, ?::text[], ?, ?, ?, ?, ?, ?, ?, ?, true, NOW())
                ON CONFLICT (source, external_id) WHERE external_id IS NOT NULL
                DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    aliases = EXCLUDED.aliases,
                    list_type = EXCLUDED.list_type,
                    country = EXCLUDED.country,
                    date_of_birth = EXCLUDED.date_of_birth,
                    national_id = EXCLUDED.national_id,
                    remarks = EXCLUDED.remarks,
                    listing_date = EXCLUDED.listing_date,
                    active = true,
                    updated_at = NOW()
                """,
                entry.fullName,
                entry.aliases.toTypedArray(),
                entry.listType,
                entry.source,
                entry.country,
                entry.dateOfBirth,
                entry.nationalId,
                entry.remarks,
                entry.listingDate,
                entry.externalId
            )
        }

        log.debug("Synced {} entries from {} to database", entries.size, source)
    }

    private fun recordUpdateTime(source: String) {
        val current = lastUpdateTimes.get().toMutableMap()
        current[source] = Instant.now()
        lastUpdateTimes.set(current)
    }

    // ============ XML Parsing Methods ============
    // These would parse the actual XML formats from each source.
    // Simplified implementations shown here.

    private fun parseMasakXml(xml: String): List<SanctionsEntry> {
        // MASAK XML format parsing
        // In production, use proper XML parser (JAXB, Jackson XML, etc.)
        val entries = mutableListOf<SanctionsEntry>()

        // Parse XML and extract entries
        // This is a placeholder - actual implementation depends on MASAK XML schema

        return entries
    }

    private fun parseUnXml(xml: String): List<SanctionsEntry> {
        // UN Consolidated List XML format
        // Schema: https://scsanctions.un.org/resources/xml/en/consolidated.xsd
        val entries = mutableListOf<SanctionsEntry>()

        // Parse UN XML structure
        // Entries are in <INDIVIDUALS> and <ENTITIES> sections

        return entries
    }

    private fun parseEuXml(xml: String): List<SanctionsEntry> {
        // EU Financial Sanctions List XML format
        val entries = mutableListOf<SanctionsEntry>()

        // Parse EU XML structure

        return entries
    }

    private fun parseOfacXml(xml: String): List<SanctionsEntry> {
        // OFAC SDN List XML format
        // Schema: https://www.treasury.gov/ofac/downloads/sdn.xsd
        val entries = mutableListOf<SanctionsEntry>()

        // Parse OFAC XML structure
        // SDN entries with aliases, addresses, IDs

        return entries
    }

    /**
     * Load initial test data for development/testing.
     * DO NOT USE IN PRODUCTION.
     */
    fun loadTestData() {
        log.warn("Loading test sanctions data - DO NOT USE IN PRODUCTION")

        val testEntries = listOf(
            SanctionsEntry(
                fullName = "Test Sanctioned Individual",
                aliases = listOf("Test Alias One", "Test Alias Two"),
                listType = "sanctions",
                source = "TEST",
                country = "XX",
                dateOfBirth = "1970-01-01",
                nationalId = null,
                remarks = "Test entry for development",
                listingDate = Instant.now(),
                externalId = "TEST-001"
            ),
            SanctionsEntry(
                fullName = "Test PEP Person",
                aliases = emptyList(),
                listType = "pep",
                source = "TEST",
                country = "XX",
                dateOfBirth = null,
                nationalId = null,
                remarks = "Test PEP for development",
                listingDate = Instant.now(),
                externalId = "TEST-002"
            )
        )

        syncEntriesToDatabase(testEntries, "TEST")
        recordUpdateTime("TEST")
    }
}

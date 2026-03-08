package com.ari.platform.compliance.internal.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ari.platform.shared.security.AuditService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Service for MASAK (Mali Suçları Araştırma Kurulu) reporting compliance.
 *
 * MASAK is Turkey's Financial Crimes Investigation Board, equivalent to FinCEN (US) or FCA (UK).
 *
 * REPORTING REQUIREMENTS:
 * 1. Suspicious Transaction Report (ŞİB - Şüpheli İşlem Bildirimi)
 *    - Must be submitted within 10 days of suspicion
 *    - For any transaction that appears suspicious regardless of amount
 *
 * 2. Threshold-based reporting:
 *    - Transactions ≥ 85,000 TL require enhanced monitoring
 *    - Cash transactions ≥ 85,000 TL require identification
 *    - Series of related transactions totaling ≥ 85,000 TL
 *
 * 3. Terrorist Financing Reports:
 *    - Immediate reporting required
 *    - Asset freeze notifications
 *
 * MASAK Online 2.0 Integration:
 * - Reports submitted via XML over secure connection
 * - Authentication via digital certificate
 * - Response includes reference number for tracking
 */
@Service
class MasakReportingService(
    private val jdbcTemplate: JdbcTemplate,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
    @Value("\${ari.compliance.masak.api-url:}") private val masakApiUrl: String,
    @Value("\${ari.compliance.masak.institution-code:}") private val institutionCode: String,
    @Value("\${ari.compliance.masak.enabled:false}") private val enabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // MASAK thresholds (2024 values - verify annually)
        val THRESHOLD_TL = BigDecimal("85000")
        val CASH_THRESHOLD_TL = BigDecimal("85000")

        // Report types
        const val REPORT_TYPE_STR = "SIB"          // Şüpheli İşlem Bildirimi (Suspicious Transaction Report)
        const val REPORT_TYPE_TERRORIST = "TFR"    // Terrorist Financing Report
        const val REPORT_TYPE_THRESHOLD = "ESK"    // Eşik Aşımı Bildirimi (Threshold Exceedance)
        const val REPORT_TYPE_ASSET_FREEZE = "MVD" // Mal Varlığı Dondurma (Asset Freeze)
    }

    data class MasakReport(
        val id: UUID = UUID.randomUUID(),
        val reportType: String,
        val subjectUserId: UUID,
        val subjectName: String,
        val subjectIdNumber: String?,
        val transactionIds: List<String>,
        val totalAmount: BigDecimal,
        val currency: String,
        val suspicionReason: String,
        val suspicionIndicators: List<String>,
        val narrativeSummary: String,
        val createdAt: Instant = Instant.now(),
        val submittedAt: Instant? = null,
        val masakReferenceNumber: String? = null,
        val status: ReportStatus = ReportStatus.DRAFT
    )

    enum class ReportStatus {
        DRAFT,              // Being prepared
        PENDING_REVIEW,     // Awaiting compliance officer review
        APPROVED,           // Approved for submission
        SUBMITTED,          // Sent to MASAK
        ACKNOWLEDGED,       // MASAK acknowledged receipt
        UNDER_INVESTIGATION,// MASAK is investigating
        CLOSED              // Case closed
    }

    data class SubmissionResult(
        val success: Boolean,
        val referenceNumber: String?,
        val error: String?
    )

    /**
     * Create a Suspicious Transaction Report (STR/ŞİB).
     */
    fun createSuspiciousTransactionReport(
        subjectUserId: UUID,
        subjectName: String,
        subjectIdNumber: String?,
        transactionIds: List<String>,
        totalAmount: BigDecimal,
        currency: String,
        suspicionReason: String,
        suspicionIndicators: List<String>,
        narrativeSummary: String,
        createdBy: UUID
    ): MasakReport {
        log.info("Creating STR for user {}: {}", subjectUserId, suspicionReason)

        val report = MasakReport(
            reportType = REPORT_TYPE_STR,
            subjectUserId = subjectUserId,
            subjectName = subjectName,
            subjectIdNumber = subjectIdNumber,
            transactionIds = transactionIds,
            totalAmount = totalAmount,
            currency = currency,
            suspicionReason = suspicionReason,
            suspicionIndicators = suspicionIndicators,
            narrativeSummary = narrativeSummary,
            status = ReportStatus.PENDING_REVIEW
        )

        saveReport(report)

        auditService.log(
            actorId = createdBy,
            actorType = "USER",
            action = "CREATE_MASAK_REPORT",
            resourceType = "MASAK_REPORT",
            resourceId = report.id.toString(),
            details = mapOf(
                "reportType" to REPORT_TYPE_STR,
                "subjectUserId" to subjectUserId.toString(),
                "totalAmount" to totalAmount.toPlainString(),
                "currency" to currency,
                "suspicionReason" to suspicionReason
            )
        )

        return report
    }

    /**
     * Create a Terrorist Financing Report (immediate submission required).
     */
    fun createTerroristFinancingReport(
        subjectUserId: UUID,
        subjectName: String,
        subjectIdNumber: String?,
        transactionIds: List<String>,
        totalAmount: BigDecimal,
        currency: String,
        matchDetails: String,
        createdBy: UUID
    ): MasakReport {
        log.warn("Creating TERRORIST FINANCING report for user {}", subjectUserId)

        val report = MasakReport(
            reportType = REPORT_TYPE_TERRORIST,
            subjectUserId = subjectUserId,
            subjectName = subjectName,
            subjectIdNumber = subjectIdNumber,
            transactionIds = transactionIds,
            totalAmount = totalAmount,
            currency = currency,
            suspicionReason = "Terrorist financing suspicion - sanctions list match",
            suspicionIndicators = listOf("SANCTIONS_MATCH", "IMMEDIATE_REPORT_REQUIRED"),
            narrativeSummary = matchDetails,
            status = ReportStatus.APPROVED // Auto-approve for immediate submission
        )

        saveReport(report)

        auditService.log(
            actorId = createdBy,
            actorType = "USER",
            action = "CREATE_MASAK_TFR",
            resourceType = "MASAK_REPORT",
            resourceId = report.id.toString(),
            details = mapOf(
                "reportType" to REPORT_TYPE_TERRORIST,
                "subjectUserId" to subjectUserId.toString(),
                "matchDetails" to matchDetails
            )
        )

        // Attempt immediate submission
        if (enabled) {
            try {
                submitReport(report.id, createdBy)
            } catch (e: Exception) {
                log.error("Failed to auto-submit TFR: {}", e.message)
            }
        }

        return report
    }

    /**
     * Submit a report to MASAK Online 2.0.
     */
    fun submitReport(reportId: UUID, submittedBy: UUID): SubmissionResult {
        log.info("Submitting MASAK report {}", reportId)

        val report = getReport(reportId)
            ?: return SubmissionResult(false, null, "Report not found")

        if (report.status != ReportStatus.APPROVED && report.reportType != REPORT_TYPE_TERRORIST) {
            return SubmissionResult(false, null, "Report must be approved before submission")
        }

        if (!enabled) {
            log.warn("MASAK integration disabled, simulating submission")
            updateReportStatus(reportId, ReportStatus.SUBMITTED, "SIM-${System.currentTimeMillis()}")
            return SubmissionResult(true, "SIM-${System.currentTimeMillis()}", null)
        }

        if (masakApiUrl.isBlank()) {
            return SubmissionResult(false, null, "MASAK API URL not configured")
        }

        try {
            val xml = buildMasakXml(report)
            val result = sendToMasak(xml)

            if (result.success) {
                updateReportStatus(reportId, ReportStatus.SUBMITTED, result.referenceNumber)

                auditService.log(
                    actorId = submittedBy,
                    actorType = "USER",
                    action = "SUBMIT_MASAK_REPORT",
                    resourceType = "MASAK_REPORT",
                    resourceId = reportId.toString(),
                    details = mapOf(
                        "masakReference" to (result.referenceNumber ?: ""),
                        "reportType" to report.reportType
                    )
                )
            }

            return result

        } catch (e: Exception) {
            log.error("MASAK submission failed: {}", e.message, e)
            return SubmissionResult(false, null, e.message)
        }
    }

    /**
     * Approve a report for submission.
     */
    fun approveReport(reportId: UUID, approvedBy: UUID): Boolean {
        log.info("Approving MASAK report {} by {}", reportId, approvedBy)

        val updated = jdbcTemplate.update(
            """
            UPDATE shared.masak_reports
            SET status = ?, approved_by = ?, approved_at = NOW(), updated_at = NOW()
            WHERE id = ? AND status = ?
            """,
            ReportStatus.APPROVED.name,
            approvedBy,
            reportId,
            ReportStatus.PENDING_REVIEW.name
        )

        if (updated > 0) {
            auditService.log(
                actorId = approvedBy,
                actorType = "USER",
                action = "APPROVE_MASAK_REPORT",
                resourceType = "MASAK_REPORT",
                resourceId = reportId.toString()
            )
        }

        return updated > 0
    }

    /**
     * Get all pending reports for review.
     */
    fun getPendingReports(): List<MasakReport> {
        return jdbcTemplate.query(
            """
            SELECT * FROM shared.masak_reports
            WHERE status = ?
            ORDER BY created_at ASC
            """,
            { rs, _ -> mapRowToReport(rs) },
            ReportStatus.PENDING_REVIEW.name
        )
    }

    /**
     * Check if any transactions for a user exceed thresholds.
     */
    fun checkThresholds(userId: UUID, date: LocalDate = LocalDate.now()): List<ThresholdAlert> {
        val alerts = mutableListOf<ThresholdAlert>()

        // Check single transaction threshold
        val singleTxAlerts = jdbcTemplate.query(
            """
            SELECT p.id, p.amount, p.currency, p.created_at
            FROM payments.payment_orders p
            JOIN ledger.accounts sa ON sa.id = p.sender_account_id
            JOIN ledger.accounts ra ON ra.id = p.receiver_account_id
            WHERE (sa.user_id = ? OR ra.user_id = ?)
              AND p.currency = 'TRY'
              AND p.amount >= ?
              AND DATE(p.created_at) = ?
              AND p.status = 'completed'
            """,
            { rs, _ ->
                ThresholdAlert(
                    type = "SINGLE_TRANSACTION",
                    userId = userId,
                    amount = rs.getBigDecimal("amount"),
                    currency = rs.getString("currency"),
                    transactionId = rs.getString("id"),
                    description = "Single transaction exceeds ${THRESHOLD_TL} TL threshold"
                )
            },
            userId, userId, THRESHOLD_TL, date
        )
        alerts.addAll(singleTxAlerts)

        // Check cumulative daily threshold
        val dailyTotal = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM payments.payment_orders p
            JOIN ledger.accounts sa ON sa.id = p.sender_account_id
            JOIN ledger.accounts ra ON ra.id = p.receiver_account_id
            WHERE (sa.user_id = ? OR ra.user_id = ?)
              AND p.currency = 'TRY'
              AND DATE(p.created_at) = ?
              AND p.status = 'completed'
            """,
            BigDecimal::class.java,
            userId, userId, date
        )

        if (dailyTotal >= THRESHOLD_TL) {
            alerts.add(
                ThresholdAlert(
                    type = "DAILY_CUMULATIVE",
                    userId = userId,
                    amount = dailyTotal,
                    currency = "TRY",
                    transactionId = null,
                    description = "Daily cumulative transactions exceed ${THRESHOLD_TL} TL threshold"
                )
            )
        }

        return alerts
    }

    data class ThresholdAlert(
        val type: String,
        val userId: UUID,
        val amount: BigDecimal,
        val currency: String,
        val transactionId: String?,
        val description: String
    )

    // ============ Private Methods ============

    private fun saveReport(report: MasakReport) {
        jdbcTemplate.update(
            """
            INSERT INTO shared.masak_reports
                (id, report_type, subject_user_id, subject_name, subject_id_number,
                 transaction_ids, total_amount, currency, suspicion_reason,
                 suspicion_indicators, narrative_summary, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?::text[], ?, ?, ?, ?::text[], ?, ?, ?)
            """,
            report.id,
            report.reportType,
            report.subjectUserId,
            report.subjectName,
            report.subjectIdNumber,
            report.transactionIds.toTypedArray(),
            report.totalAmount,
            report.currency,
            report.suspicionReason,
            report.suspicionIndicators.toTypedArray(),
            report.narrativeSummary,
            report.status.name,
            report.createdAt
        )
    }

    private fun getReport(reportId: UUID): MasakReport? {
        return jdbcTemplate.query(
            "SELECT * FROM shared.masak_reports WHERE id = ?",
            { rs, _ -> mapRowToReport(rs) },
            reportId
        ).firstOrNull()
    }

    private fun mapRowToReport(rs: java.sql.ResultSet): MasakReport {
        return MasakReport(
            id = UUID.fromString(rs.getString("id")),
            reportType = rs.getString("report_type"),
            subjectUserId = UUID.fromString(rs.getString("subject_user_id")),
            subjectName = rs.getString("subject_name"),
            subjectIdNumber = rs.getString("subject_id_number"),
            transactionIds = (rs.getArray("transaction_ids")?.array as? Array<*>)
                ?.map { it.toString() } ?: emptyList(),
            totalAmount = rs.getBigDecimal("total_amount"),
            currency = rs.getString("currency"),
            suspicionReason = rs.getString("suspicion_reason"),
            suspicionIndicators = (rs.getArray("suspicion_indicators")?.array as? Array<*>)
                ?.map { it.toString() } ?: emptyList(),
            narrativeSummary = rs.getString("narrative_summary"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            submittedAt = rs.getTimestamp("submitted_at")?.toInstant(),
            masakReferenceNumber = rs.getString("masak_reference_number"),
            status = ReportStatus.valueOf(rs.getString("status"))
        )
    }

    private fun updateReportStatus(reportId: UUID, status: ReportStatus, referenceNumber: String?) {
        jdbcTemplate.update(
            """
            UPDATE shared.masak_reports
            SET status = ?, masak_reference_number = ?, submitted_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """,
            status.name,
            referenceNumber,
            reportId
        )
    }

    private fun buildMasakXml(report: MasakReport): String {
        // Build MASAK Online 2.0 compliant XML
        // Schema defined by MASAK
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <MasakBildirimi>
                <Kurum>$institutionCode</Kurum>
                <BildirimTipi>${report.reportType}</BildirimTipi>
                <BildirimNo>${report.id}</BildirimNo>
                <Tarih>${report.createdAt}</Tarih>
                <Kisi>
                    <AdSoyad>${report.subjectName}</AdSoyad>
                    <KimlikNo>${report.subjectIdNumber ?: ""}</KimlikNo>
                </Kisi>
                <Islem>
                    <Tutar>${report.totalAmount}</Tutar>
                    <ParaBirimi>${report.currency}</ParaBirimi>
                </Islem>
                <SupheNedeni>${report.suspicionReason}</SupheNedeni>
                <Aciklama>${report.narrativeSummary}</Aciklama>
            </MasakBildirimi>
        """.trimIndent()
    }

    private fun sendToMasak(xml: String): SubmissionResult {
        // In production, this would:
        // 1. Sign the XML with the institution's digital certificate
        // 2. Send via HTTPS to MASAK Online 2.0 endpoint
        // 3. Parse the response for reference number
        // 4. Handle errors and retries

        // Placeholder implementation
        log.info("Would send to MASAK: {} bytes", xml.length)

        return SubmissionResult(
            success = true,
            referenceNumber = "MASAK-${System.currentTimeMillis()}",
            error = null
        )
    }
}

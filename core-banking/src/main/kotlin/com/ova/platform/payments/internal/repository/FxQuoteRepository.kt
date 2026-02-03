package com.ova.platform.payments.internal.repository

import com.ova.platform.payments.internal.model.FxQuote
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class FxQuoteRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        FxQuote(
            id = UUID.fromString(rs.getString("id")),
            sourceCurrency = rs.getString("source_currency"),
            targetCurrency = rs.getString("target_currency"),
            exchangeRate = rs.getBigDecimal("exchange_rate"),
            sourceAmount = rs.getBigDecimal("source_amount"),
            targetAmount = rs.getBigDecimal("target_amount"),
            feeAmount = rs.getBigDecimal("fee_amount"),
            feeCurrency = rs.getString("fee_currency"),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            used = rs.getBoolean("used"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(quote: FxQuote): FxQuote {
        jdbcTemplate.update(
            """
            INSERT INTO payments.fx_quotes
                (id, source_currency, target_currency, exchange_rate,
                 source_amount, target_amount, fee_amount, fee_currency, expires_at, used)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            quote.id,
            quote.sourceCurrency,
            quote.targetCurrency,
            quote.exchangeRate,
            quote.sourceAmount,
            quote.targetAmount,
            quote.feeAmount,
            quote.feeCurrency,
            java.sql.Timestamp.from(quote.expiresAt),
            quote.used
        )
        return quote
    }

    fun findById(id: UUID): FxQuote? {
        return jdbcTemplate.query(
            "SELECT * FROM payments.fx_quotes WHERE id = ?", rowMapper, id
        ).firstOrNull()
    }

    fun markAsUsed(id: UUID) {
        jdbcTemplate.update(
            "UPDATE payments.fx_quotes SET used = true WHERE id = ?", id
        )
    }
}

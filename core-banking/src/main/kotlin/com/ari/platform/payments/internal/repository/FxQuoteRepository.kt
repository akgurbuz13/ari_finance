package com.ari.platform.payments.internal.repository

import com.ari.platform.payments.internal.model.FxQuote
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class FxQuoteRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        val exchangeRate = rs.getBigDecimal("exchange_rate")
            ?: rs.getBigDecimal("customer_rate")
            ?: rs.getBigDecimal("rate")
            ?: throw IllegalStateException("FX quote ${rs.getString("id")} has no exchange rate")
        val feeAmount = rs.getBigDecimal("fee_amount") ?: java.math.BigDecimal.ZERO
        val feeCurrency = rs.getString("fee_currency") ?: rs.getString("target_currency")

        FxQuote(
            id = UUID.fromString(rs.getString("id")),
            sourceCurrency = rs.getString("source_currency"),
            targetCurrency = rs.getString("target_currency"),
            exchangeRate = exchangeRate,
            sourceAmount = rs.getBigDecimal("source_amount"),
            targetAmount = rs.getBigDecimal("target_amount"),
            feeAmount = feeAmount,
            feeCurrency = feeCurrency,
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            used = rs.getBoolean("used"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    fun save(quote: FxQuote): FxQuote {
        jdbcTemplate.update(
            """
            INSERT INTO payments.fx_quotes
                (id, source_currency, target_currency, rate, inverse_rate, spread,
                 source_amount, target_amount, exchange_rate, fee_amount, fee_currency,
                 mid_market_rate, customer_rate, expires_at, used, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            quote.id,
            quote.sourceCurrency,
            quote.targetCurrency,
            quote.exchangeRate,
            if (quote.exchangeRate.signum() == 0) java.math.BigDecimal.ZERO
            else java.math.BigDecimal.ONE.divide(quote.exchangeRate, 10, java.math.RoundingMode.HALF_UP),
            java.math.BigDecimal.ZERO,
            quote.exchangeRate,
            quote.sourceAmount,
            quote.targetAmount,
            quote.feeAmount,
            quote.feeCurrency,
            quote.exchangeRate,
            quote.exchangeRate,
            java.sql.Timestamp.from(quote.expiresAt),
            quote.used,
            if (quote.used) "consumed" else "active"
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
            "UPDATE payments.fx_quotes SET used = true, status = 'consumed', consumed_at = NOW() WHERE id = ?",
            id
        )
    }
}

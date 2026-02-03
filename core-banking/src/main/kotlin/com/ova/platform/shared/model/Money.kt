package com.ova.platform.shared.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amount: BigDecimal,
    val currency: Currency
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Amount cannot be negative" }
    }

    fun add(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies: $currency and ${other.currency}" }
        return Money(amount.add(other.amount), currency)
    }

    fun subtract(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies: $currency and ${other.currency}" }
        val result = amount.subtract(other.amount)
        require(result >= BigDecimal.ZERO) { "Insufficient funds" }
        return Money(result, currency)
    }

    fun multiply(factor: BigDecimal): Money =
        Money(amount.multiply(factor).setScale(8, RoundingMode.HALF_UP), currency)

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    companion object {
        fun of(amount: String, currency: Currency): Money =
            Money(BigDecimal(amount), currency)

        fun zero(currency: Currency): Money =
            Money(BigDecimal.ZERO, currency)
    }
}

enum class Currency(val code: String, val decimalPlaces: Int) {
    TRY("TRY", 2),
    EUR("EUR", 2);

    companion object {
        fun fromCode(code: String): Currency =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unsupported currency: $code")
    }
}

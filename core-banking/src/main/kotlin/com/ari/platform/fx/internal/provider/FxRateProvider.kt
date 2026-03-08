package com.ari.platform.fx.internal.provider

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/**
 * Contract for fetching foreign exchange rates.
 * Implementations may pull from external market data feeds, central bank APIs, or stub sources.
 */
interface FxRateProvider {

    /**
     * Fetch the exchange rate for converting [sourceCurrency] to [targetCurrency].
     * Returns a [FxRate] containing the rate, inverse rate, and metadata.
     */
    fun getRate(sourceCurrency: String, targetCurrency: String): FxRate
}

data class FxRate(
    val sourceCurrency: String,
    val targetCurrency: String,
    val rate: BigDecimal,
    val inverseRate: BigDecimal,
    val fetchedAt: Instant = Instant.now()
)

/**
 * Stub FX rate provider that returns hardcoded TRY/EUR mid-market rates.
 * Uses Redis caching to reduce calls to the (future) external rate feed.
 *
 * Cache TTL is 60 seconds to balance freshness with performance.
 */
@Component
class CachedFxRateProvider(
    private val redisTemplate: RedisTemplate<String, Any>
) : FxRateProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CACHE_KEY_PREFIX = "fx:rate:"
        private val CACHE_TTL: Duration = Duration.ofSeconds(60)
        private val MATH_CONTEXT = MathContext(10, RoundingMode.HALF_UP)

        // Stub mid-market rates (in production these come from a market data feed)
        private val STUB_RATES: Map<String, BigDecimal> = mapOf(
            "TRY/EUR" to BigDecimal("0.0263"),
            "EUR/TRY" to BigDecimal("38.0228")
        )
    }

    override fun getRate(sourceCurrency: String, targetCurrency: String): FxRate {
        if (sourceCurrency == targetCurrency) {
            return FxRate(
                sourceCurrency = sourceCurrency,
                targetCurrency = targetCurrency,
                rate = BigDecimal.ONE,
                inverseRate = BigDecimal.ONE
            )
        }

        val cacheKey = "$CACHE_KEY_PREFIX${sourceCurrency}/${targetCurrency}"

        // Try Redis cache first
        val cached = tryGetCached(cacheKey)
        if (cached != null) {
            log.debug("FX rate cache hit for {}/{}", sourceCurrency, targetCurrency)
            return cached
        }

        // Fetch rate (stub implementation)
        val rate = fetchRate(sourceCurrency, targetCurrency)

        // Cache the result
        try {
            redisTemplate.opsForValue().set(cacheKey, rate.rate.toPlainString(), CACHE_TTL)
            log.debug("Cached FX rate {}/{}={} ttl={}s", sourceCurrency, targetCurrency, rate.rate, CACHE_TTL.seconds)
        } catch (e: Exception) {
            log.warn("Failed to cache FX rate: {}", e.message)
        }

        return rate
    }

    private fun fetchRate(sourceCurrency: String, targetCurrency: String): FxRate {
        val pairKey = "$sourceCurrency/$targetCurrency"
        val rate = STUB_RATES[pairKey]
            ?: throw IllegalArgumentException("No rate available for pair: $pairKey")

        val inverseRate = BigDecimal.ONE.divide(rate, MATH_CONTEXT)

        log.info("Fetched FX rate {}={}, inverse={}", pairKey, rate, inverseRate)

        return FxRate(
            sourceCurrency = sourceCurrency,
            targetCurrency = targetCurrency,
            rate = rate,
            inverseRate = inverseRate
        )
    }

    private fun tryGetCached(cacheKey: String): FxRate? {
        return try {
            val cached = redisTemplate.opsForValue().get(cacheKey) as? String ?: return null
            val rate = BigDecimal(cached)
            val parts = cacheKey.removePrefix(CACHE_KEY_PREFIX).split("/")
            val inverseRate = BigDecimal.ONE.divide(rate, MATH_CONTEXT)
            FxRate(
                sourceCurrency = parts[0],
                targetCurrency = parts[1],
                rate = rate,
                inverseRate = inverseRate
            )
        } catch (e: Exception) {
            log.debug("Cache read failed for key={}: {}", cacheKey, e.message)
            null
        }
    }
}

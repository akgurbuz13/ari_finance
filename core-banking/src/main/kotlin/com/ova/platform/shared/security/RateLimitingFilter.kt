package com.ova.platform.shared.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiting filter to prevent brute force attacks on authentication endpoints.
 *
 * Uses token bucket algorithm via Bucket4j for efficient rate limiting.
 * Limits are applied per IP address for unauthenticated endpoints.
 */
@Component
@Order(1) // Run before authentication filters
class RateLimitingFilter(
    @Value("\${ari.rate-limit.auth.requests-per-minute:10}") private val authRequestsPerMinute: Long,
    @Value("\${ari.rate-limit.auth.requests-per-hour:100}") private val authRequestsPerHour: Long,
    @Value("\${ari.rate-limit.general.requests-per-minute:60}") private val generalRequestsPerMinute: Long
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    // IP-based buckets for auth endpoints (stricter limits)
    private val authBuckets = ConcurrentHashMap<String, Bucket>()

    // IP-based buckets for general endpoints
    private val generalBuckets = ConcurrentHashMap<String, Bucket>()

    // Maximum bucket cache size to prevent memory exhaustion
    private val maxBucketCacheSize = 10_000

    companion object {
        private val AUTH_PATHS = setOf(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/2fa/enable"
        )

        private const val RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining"
        private const val RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit"
        private const val RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset"
        private const val RETRY_AFTER_HEADER = "Retry-After"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = getClientIpAddress(request)
        val requestPath = request.requestURI

        // Determine which rate limit applies
        val isAuthEndpoint = AUTH_PATHS.any { requestPath.startsWith(it) }

        val bucket = if (isAuthEndpoint) {
            getOrCreateAuthBucket(clientIp)
        } else if (requestPath.startsWith("/api/")) {
            getOrCreateGeneralBucket(clientIp)
        } else {
            // Non-API endpoints (health checks, etc.) - no rate limiting
            filterChain.doFilter(request, response)
            return
        }

        val probe = bucket.tryConsumeAndReturnRemaining(1)

        if (probe.isConsumed) {
            // Add rate limit headers
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, probe.remainingTokens.toString())
            response.setHeader(
                RATE_LIMIT_LIMIT_HEADER,
                if (isAuthEndpoint) authRequestsPerMinute.toString() else generalRequestsPerMinute.toString()
            )

            filterChain.doFilter(request, response)
        } else {
            // Rate limit exceeded
            val waitTimeSeconds = probe.nanosToWaitForRefill / 1_000_000_000

            log.warn(
                "Rate limit exceeded for IP: {} on path: {} - wait time: {}s",
                clientIp,
                requestPath,
                waitTimeSeconds
            )

            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.setHeader(RETRY_AFTER_HEADER, waitTimeSeconds.toString())
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, "0")
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"rate_limit_exceeded","message":"Too many requests. Please try again later.","retryAfterSeconds":$waitTimeSeconds}"""
            )
        }
    }

    /**
     * Extract client IP address, considering proxy headers.
     */
    private fun getClientIpAddress(request: HttpServletRequest): String {
        // Check standard proxy headers in order of preference
        val forwardedFor = request.getHeader("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            // X-Forwarded-For can contain multiple IPs; take the first (original client)
            return forwardedFor.split(",").first().trim()
        }

        val realIp = request.getHeader("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp.trim()
        }

        return request.remoteAddr ?: "unknown"
    }

    private fun getOrCreateAuthBucket(clientIp: String): Bucket {
        cleanupBucketsIfNeeded(authBuckets)

        return authBuckets.computeIfAbsent(clientIp) {
            Bucket.builder()
                // Short-term limit: X requests per minute
                .addLimit(
                    Bandwidth.classic(
                        authRequestsPerMinute,
                        Refill.greedy(authRequestsPerMinute, Duration.ofMinutes(1))
                    )
                )
                // Long-term limit: X requests per hour (prevents sustained attacks)
                .addLimit(
                    Bandwidth.classic(
                        authRequestsPerHour,
                        Refill.greedy(authRequestsPerHour, Duration.ofHours(1))
                    )
                )
                .build()
        }
    }

    private fun getOrCreateGeneralBucket(clientIp: String): Bucket {
        cleanupBucketsIfNeeded(generalBuckets)

        return generalBuckets.computeIfAbsent(clientIp) {
            Bucket.builder()
                .addLimit(
                    Bandwidth.classic(
                        generalRequestsPerMinute,
                        Refill.greedy(generalRequestsPerMinute, Duration.ofMinutes(1))
                    )
                )
                .build()
        }
    }

    /**
     * Prevent memory exhaustion by clearing buckets when cache gets too large.
     * In production, use Redis-backed buckets for distributed rate limiting.
     */
    private fun cleanupBucketsIfNeeded(buckets: ConcurrentHashMap<String, Bucket>) {
        if (buckets.size > maxBucketCacheSize) {
            log.warn("Rate limit bucket cache exceeded {} entries, clearing oldest entries", maxBucketCacheSize)
            // Simple cleanup: remove half the entries
            val keysToRemove = buckets.keys.take(buckets.size / 2)
            keysToRemove.forEach { buckets.remove(it) }
        }
    }
}

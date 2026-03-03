package com.ova.platform.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Filter to authenticate internal service-to-service calls via API key.
 *
 * In production, this should be supplemented or replaced by mTLS via a service mesh.
 * The API key provides an additional layer of security for internal endpoints.
 */
@Component
class InternalApiKeyFilter(
    @Value("\${ari.internal.api-key:}") private val configuredApiKey: String
) : OncePerRequestFilter() {

    companion object {
        private const val API_KEY_HEADER = "X-Internal-Api-Key"
        private const val INTERNAL_PATH_PREFIX = "/api/internal/"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestPath = request.requestURI

        // Only process internal API calls
        if (!requestPath.startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response)
            return
        }

        // Check if already authenticated
        if (SecurityContextHolder.getContext().authentication?.isAuthenticated == true) {
            filterChain.doFilter(request, response)
            return
        }

        val providedApiKey = request.getHeader(API_KEY_HEADER)

        if (providedApiKey.isNullOrBlank()) {
            logger.warn("Missing internal API key for path: $requestPath from IP: ${request.remoteAddr}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing internal API key")
            return
        }

        if (configuredApiKey.isBlank()) {
            logger.error("Internal API key not configured - rejecting request to: $requestPath")
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service not configured")
            return
        }

        // Constant-time comparison to prevent timing attacks
        if (!constantTimeEquals(providedApiKey, configuredApiKey)) {
            logger.warn("Invalid internal API key for path: $requestPath from IP: ${request.remoteAddr}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal API key")
            return
        }

        // Create authentication with SYSTEM role for internal service calls
        val authorities = listOf(SimpleGrantedAuthority("ROLE_SYSTEM"))
        val authentication = UsernamePasswordAuthenticationToken(
            "SYSTEM_SERVICE",
            null,
            authorities
        )
        SecurityContextHolder.getContext().authentication = authentication

        logger.debug("Authenticated internal service call to: $requestPath")
        filterChain.doFilter(request, response)
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }
}

package com.ova.platform.shared.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val internalApiKeyFilter: InternalApiKeyFilter,
    private val environment: Environment,
    @Value("\${ari.cors.allowed-origins:}") private val allowedOrigins: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password"
                    ).permitAll()
                    .requestMatchers("/api/v1/webhooks/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Swagger/OpenAPI - only in dev/staging
                    .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").access { _, _ ->
                        val activeProfiles = environment.activeProfiles
                        org.springframework.security.authorization.AuthorizationDecision(
                            activeProfiles.isEmpty() ||
                            activeProfiles.contains("dev") ||
                            activeProfiles.contains("staging")
                        )
                    }
                    // Internal endpoints - secured via API key (mTLS in production via service mesh)
                    .requestMatchers("/api/internal/**").hasRole("SYSTEM")
                    // Admin endpoints
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Parse allowed origins from configuration - NO wildcards in production
        val origins = if (allowedOrigins.isNotBlank()) {
            allowedOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            // Default allowed origins for development only
            val activeProfiles = environment.activeProfiles
            if (activeProfiles.isEmpty() || activeProfiles.contains("dev")) {
                listOf("http://localhost:3000", "http://localhost:3001", "http://localhost:8080")
            } else {
                // Production: no default origins - must be configured
                emptyList()
            }
        }

        configuration.allowedOrigins = origins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "Idempotency-Key",
            "X-Correlation-Id"
        )
        configuration.exposedHeaders = listOf("X-Correlation-Id", "X-Request-Id")
        configuration.allowCredentials = true
        configuration.maxAge = 3600

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

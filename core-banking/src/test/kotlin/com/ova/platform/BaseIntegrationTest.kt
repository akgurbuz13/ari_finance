package com.ova.platform

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseIntegrationTest {

    companion object {
        private val useTestcontainers = System.getenv("SPRING_DATASOURCE_URL").isNullOrBlank()

        private val postgres: PostgreSQLContainer<*>? = if (useTestcontainers) {
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("ova")
                withUsername("ova")
                withPassword("ova_test")
                start()
            }
        } else null

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (postgres != null) {
                registry.add("spring.datasource.url") { postgres.jdbcUrl }
                registry.add("spring.datasource.username") { postgres.username }
                registry.add("spring.datasource.password") { postgres.password }
            }
            // Redis: use CI env vars if available, otherwise local dev defaults
            registry.add("spring.data.redis.host") { System.getenv("SPRING_DATA_REDIS_HOST") ?: "localhost" }
            registry.add("spring.data.redis.port") { System.getenv("SPRING_DATA_REDIS_PORT") ?: "16379" }
            // Provide test secrets
            registry.add("ari.jwt.secret") { "test-jwt-secret-must-be-at-least-32-characters-long-for-validation" }
            registry.add("ari.internal.api-key") { "test-internal-api-key" }
        }
    }
}

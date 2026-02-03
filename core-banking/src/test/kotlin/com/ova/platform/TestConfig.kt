package com.ova.platform

import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import io.mockk.mockk

@TestConfiguration
class TestConfig {

    @Bean
    @Primary
    fun redisConnectionFactory(): RedisConnectionFactory = mockk(relaxed = true)

    @Bean
    @Primary
    fun redisTemplate(): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.keySerializer = StringRedisSerializer()
        template.setDefaultSerializer(StringRedisSerializer())
        return template
    }
}

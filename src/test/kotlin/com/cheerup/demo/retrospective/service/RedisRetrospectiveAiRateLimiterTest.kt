package com.cheerup.demo.retrospective.service

import com.cheerup.demo.retrospective.ai.RetrospectiveAiProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RedisRetrospectiveAiRateLimiterTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var properties: RetrospectiveAiProperties

    private val userId = 99L

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        valueOperations = mockk()
        properties = RetrospectiveAiProperties().apply {
            dailyLimit = 2
        }
        every { redisTemplate.opsForValue() } returns valueOperations
    }

    @Test
    fun `first call increments redis key and sets ttl until next UTC midnight`() {
        val key = "retrospective:ai:rate:99:20260602"
        val limiter = limiterAt("2026-06-02T10:00:00Z")

        every { valueOperations.increment(key) } returns 1L
        every { redisTemplate.expire(key, Duration.ofHours(14)) } returns true

        assertThat(limiter.tryAcquire(userId)).isTrue()

        verify(exactly = 1) { valueOperations.increment(key) }
        verify(exactly = 1) { redisTemplate.expire(key, Duration.ofHours(14)) }
    }

    @Test
    fun `daily limit allows counts up to limit and rejects over limit`() {
        val key = "retrospective:ai:rate:99:20260602"
        val limiter = limiterAt("2026-06-02T00:00:00Z")

        every { valueOperations.increment(key) } returnsMany listOf(1L, 2L, 3L)
        every { redisTemplate.expire(key, Duration.ofDays(1)) } returns true

        assertThat(limiter.tryAcquire(userId)).isTrue()
        assertThat(limiter.tryAcquire(userId)).isTrue()
        assertThat(limiter.tryAcquire(userId)).isFalse()

        verify(exactly = 3) { valueOperations.increment(key) }
        verify(exactly = 1) { redisTemplate.expire(key, Duration.ofDays(1)) }
    }

    @Test
    fun `UTC date change uses a different key`() {
        val beforeMidnight = limiterAt("2026-06-02T23:59:00Z")
        val afterMidnight = limiterAt("2026-06-03T00:00:00Z")
        val firstKey = "retrospective:ai:rate:99:20260602"
        val secondKey = "retrospective:ai:rate:99:20260603"

        every { valueOperations.increment(firstKey) } returns 1L
        every { valueOperations.increment(secondKey) } returns 1L
        every { redisTemplate.expire(firstKey, Duration.ofMinutes(1)) } returns true
        every { redisTemplate.expire(secondKey, Duration.ofDays(1)) } returns true

        assertThat(beforeMidnight.tryAcquire(userId)).isTrue()
        assertThat(afterMidnight.tryAcquire(userId)).isTrue()

        verify(exactly = 1) { valueOperations.increment(firstKey) }
        verify(exactly = 1) { valueOperations.increment(secondKey) }
    }

    @Test
    fun `non-positive daily limit rejects without touching redis`() {
        properties.dailyLimit = 0
        val limiter = limiterAt("2026-06-02T10:00:00Z")

        assertThat(limiter.tryAcquire(userId)).isFalse()

        verify(exactly = 0) { redisTemplate.opsForValue() }
    }

    private fun limiterAt(instant: String): RedisRetrospectiveAiRateLimiter =
        RedisRetrospectiveAiRateLimiter(
            redisTemplate = redisTemplate,
            properties = properties,
            clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
        )
}

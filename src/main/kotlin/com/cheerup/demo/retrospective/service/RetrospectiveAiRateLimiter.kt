package com.cheerup.demo.retrospective.service

import com.cheerup.demo.retrospective.ai.RetrospectiveAiProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface RetrospectiveAiRateLimiter {
    fun tryAcquire(userId: Long): Boolean
}

@Component
class RedisRetrospectiveAiRateLimiter(
    private val redisTemplate: StringRedisTemplate,
    private val properties: RetrospectiveAiProperties,
    private val clock: Clock,
) : RetrospectiveAiRateLimiter {

    override fun tryAcquire(userId: Long): Boolean {
        val dailyLimit = properties.dailyLimit
        if (dailyLimit <= 0) {
            return false
        }

        val now = clock.instant()
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        val key = key(userId, today)
        val count = redisTemplate.opsForValue().increment(key) ?: return false
        if (count == 1L) {
            redisTemplate.expire(key, ttlUntilNextUtcMidnight(now, today))
        }

        return count <= dailyLimit
    }

    private fun key(userId: Long, date: LocalDate): String =
        "$KEY_PREFIX:$userId:${date.format(DateTimeFormatter.BASIC_ISO_DATE)}"

    private fun ttlUntilNextUtcMidnight(now: java.time.Instant, today: LocalDate): Duration {
        val nextMidnight = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return Duration.between(now, nextMidnight)
    }

    companion object {
        const val KEY_PREFIX = "retrospective:ai:rate"
    }
}

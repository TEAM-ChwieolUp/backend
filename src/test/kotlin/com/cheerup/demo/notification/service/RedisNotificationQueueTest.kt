package com.cheerup.demo.notification.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RedisNotificationQueueTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var zSetOperations: ZSetOperations<String, String>
    private lateinit var queue: RedisNotificationQueue

    private val now = Instant.parse("2026-06-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        zSetOperations = mockk(relaxed = true)
        every { redisTemplate.opsForZSet() } returns zSetOperations

        queue = RedisNotificationQueue(
            redisTemplate = redisTemplate,
            reminderCalculator = NotificationReminderCalculator(),
            clock = clock,
        )
    }

    @Test
    fun `enqueueApplicationDeadline stores future reminders in due zset`() {
        val deadlineAt = Instant.parse("2026-06-10T09:00:00Z")

        queue.enqueueApplicationDeadline(
            userId = 99L,
            applicationId = 101L,
            deadlineAt = deadlineAt,
        )

        verify(exactly = 1) {
            zSetOperations.add(
                RedisNotificationQueue.DUE_KEY,
                "APPLICATION:101:D_MINUS_3:99",
                Instant.parse("2026-06-07T09:00:00Z").toEpochMilli().toDouble(),
            )
        }
        verify(exactly = 1) {
            zSetOperations.add(
                RedisNotificationQueue.DUE_KEY,
                "APPLICATION:101:D_MINUS_1:99",
                Instant.parse("2026-06-09T09:00:00Z").toEpochMilli().toDouble(),
            )
        }
        verify(exactly = 1) {
            zSetOperations.add(
                RedisNotificationQueue.DUE_KEY,
                "APPLICATION:101:D_DAY:99",
                Instant.parse("2026-06-10T09:00:00Z").toEpochMilli().toDouble(),
            )
        }
    }

    @Test
    fun `updateApplicationDeadline removes old reminders before enqueueing new reminders`() {
        val deadlineAt = Instant.parse("2026-06-12T09:00:00Z")

        queue.updateApplicationDeadline(
            userId = 99L,
            applicationId = 101L,
            deadlineAt = deadlineAt,
        )

        verifyOrder {
            zSetOperations.remove(
                RedisNotificationQueue.DUE_KEY,
                "APPLICATION:101:D_MINUS_3:99",
                "APPLICATION:101:D_MINUS_1:99",
                "APPLICATION:101:D_DAY:99",
            )
            zSetOperations.add(
                RedisNotificationQueue.DUE_KEY,
                "APPLICATION:101:D_MINUS_3:99",
                Instant.parse("2026-06-09T09:00:00Z").toEpochMilli().toDouble(),
            )
        }
    }

    @Test
    fun `removeByEventId deletes all schedule event reminder values`() {
        queue.removeByEventId(userId = 99L, eventId = 501L)

        verify(exactly = 1) {
            zSetOperations.remove(
                RedisNotificationQueue.DUE_KEY,
                "SCHEDULE_EVENT:501:D_MINUS_3:99",
                "SCHEDULE_EVENT:501:D_MINUS_1:99",
                "SCHEDULE_EVENT:501:D_DAY:99",
            )
        }
        verify(exactly = 0) {
            zSetOperations.add(any(), any(), any<Double>())
        }
    }
}

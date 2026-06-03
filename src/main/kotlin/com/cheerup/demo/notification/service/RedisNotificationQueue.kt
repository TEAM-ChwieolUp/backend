package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class RedisNotificationQueue(
    private val redisTemplate: StringRedisTemplate,
    private val reminderCalculator: NotificationReminderCalculator,
    private val clock: Clock,
) : NotificationQueue, NotificationDueQueue {

    override fun enqueueApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant) {
        enqueue(
            userId = userId,
            sourceType = NotificationSourceType.APPLICATION,
            sourceId = applicationId,
            scheduledAt = deadlineAt,
        )
    }

    override fun updateApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant) {
        removeByApplicationId(userId, applicationId)
        enqueueApplicationDeadline(userId, applicationId, deadlineAt)
    }

    override fun removeByApplicationId(userId: Long, applicationId: Long) {
        removeAllReminderValues(
            userId = userId,
            sourceType = NotificationSourceType.APPLICATION,
            sourceId = applicationId,
        )
    }

    override fun enqueueScheduleEvent(userId: Long, eventId: Long, startAt: Instant) {
        enqueue(
            userId = userId,
            sourceType = NotificationSourceType.SCHEDULE_EVENT,
            sourceId = eventId,
            scheduledAt = startAt,
        )
    }

    override fun updateScheduleEvent(userId: Long, eventId: Long, startAt: Instant) {
        removeByEventId(userId, eventId)
        enqueueScheduleEvent(userId, eventId, startAt)
    }

    override fun removeByEventId(userId: Long, eventId: Long) {
        removeAllReminderValues(
            userId = userId,
            sourceType = NotificationSourceType.SCHEDULE_EVENT,
            sourceId = eventId,
        )
    }

    override fun findDue(now: Instant, limit: Long): Set<String> =
        redisTemplate.opsForZSet()
            .rangeByScore(DUE_KEY, Double.NEGATIVE_INFINITY, now.toEpochMilli().toDouble(), 0, limit)
            .orEmpty()

    override fun removeValue(value: String) {
        redisTemplate.opsForZSet().remove(DUE_KEY, value)
    }

    private fun enqueue(
        userId: Long,
        sourceType: NotificationSourceType,
        sourceId: Long,
        scheduledAt: Instant,
    ) {
        val now = clock.instant()
        reminderCalculator.futureReminders(scheduledAt, now).forEach { reminder ->
            val value = NotificationQueueItem(
                sourceType = sourceType,
                sourceId = sourceId,
                remindType = reminder.remindType,
                userId = userId,
            ).toQueueValue()
            redisTemplate.opsForZSet().add(DUE_KEY, value, reminder.triggerAt.toEpochMilli().toDouble())
        }
    }

    private fun removeAllReminderValues(
        userId: Long,
        sourceType: NotificationSourceType,
        sourceId: Long,
    ) {
        val values = NotificationRemindType.entries
            .map { remindType ->
                NotificationQueueItem(
                    sourceType = sourceType,
                    sourceId = sourceId,
                    remindType = remindType,
                    userId = userId,
                ).toQueueValue()
            }

        redisTemplate.opsForZSet().remove(DUE_KEY, *values.toTypedArray())
    }

    companion object {
        const val DUE_KEY = "notifications:due"
    }
}

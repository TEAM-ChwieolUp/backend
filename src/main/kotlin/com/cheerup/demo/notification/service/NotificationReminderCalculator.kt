package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class NotificationReminderCalculator {

    fun futureReminders(
        scheduledAt: Instant,
        now: Instant,
    ): List<NotificationReminder> {
        if (!scheduledAt.isAfter(now)) {
            return emptyList()
        }

        val remindTypes = NotificationRemindType.entries
        return remindTypes
            .mapIndexedNotNull { index, remindType ->
                val thresholdAt = scheduledAt.minus(remindType.offset)
                val nextThresholdAt = remindTypes
                    .getOrNull(index + 1)
                    ?.let { nextType -> scheduledAt.minus(nextType.offset) }
                    ?: scheduledAt

                when {
                    thresholdAt.isAfter(now) -> NotificationReminder(remindType, thresholdAt)
                    now.isBefore(nextThresholdAt) -> NotificationReminder(remindType, now)
                    else -> null
                }
            }
    }
}

data class NotificationReminder(
    val remindType: NotificationRemindType,
    val triggerAt: Instant,
)

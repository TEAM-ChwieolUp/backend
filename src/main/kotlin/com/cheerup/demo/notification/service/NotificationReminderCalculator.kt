package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class NotificationReminderCalculator {

    fun futureReminders(
        scheduledAt: Instant,
        now: Instant,
    ): List<NotificationReminder> =
        NotificationRemindType.entries
            .map { remindType ->
                NotificationReminder(
                    remindType = remindType,
                    triggerAt = scheduledAt.minus(remindType.offset),
                )
            }
            .filter { it.triggerAt.isAfter(now) }
}

data class NotificationReminder(
    val remindType: NotificationRemindType,
    val triggerAt: Instant,
)

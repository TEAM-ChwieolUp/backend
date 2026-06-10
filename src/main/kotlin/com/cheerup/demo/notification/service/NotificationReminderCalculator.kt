package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Component
class NotificationReminderCalculator {

    fun futureReminders(
        scheduledAt: Instant,
        now: Instant,
    ): List<NotificationReminder> {
        if (!scheduledAt.isAfter(now)) {
            return emptyList()
        }

        val scheduledDate = scheduledAt.atZone(REMINDER_ZONE).toLocalDate()
        val today = now.atZone(REMINDER_ZONE).toLocalDate()

        return NotificationRemindType.entries
            .mapNotNull { remindType ->
                val reminderDate = scheduledDate.minusDays(remindType.offset.toDays())
                val reminderAt = reminderDate
                    .atTime(DEFAULT_REMINDER_TIME)
                    .atZone(REMINDER_ZONE)
                    .toInstant()
                val triggerAt = if (remindType == NotificationRemindType.D_DAY && reminderAt.isAfter(scheduledAt)) {
                    scheduledAt
                } else {
                    reminderAt
                }

                when {
                    triggerAt.isAfter(now) -> NotificationReminder(remindType, triggerAt)
                    reminderDate == today -> NotificationReminder(remindType, now)
                    else -> null
                }
            }
    }

    companion object {
        private val REMINDER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

data class NotificationReminder(
    val remindType: NotificationRemindType,
    val triggerAt: Instant,
)

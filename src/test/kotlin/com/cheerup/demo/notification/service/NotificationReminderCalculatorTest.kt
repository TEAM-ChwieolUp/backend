package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationReminderCalculatorTest {

    private val calculator = NotificationReminderCalculator()

    @Test
    fun `futureReminders returns D-3 D-1 and D-Day trigger times`() {
        val scheduledAt = Instant.parse("2026-06-10T09:00:00Z")
        val now = Instant.parse("2026-06-01T00:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders.map { it.remindType }).containsExactly(
            NotificationRemindType.D_MINUS_3,
            NotificationRemindType.D_MINUS_1,
            NotificationRemindType.D_DAY,
        )
        assertThat(reminders.map { it.triggerAt }).containsExactly(
            Instant.parse("2026-06-07T09:00:00Z"),
            Instant.parse("2026-06-09T09:00:00Z"),
            Instant.parse("2026-06-10T09:00:00Z"),
        )
    }

    @Test
    fun `futureReminders skips already passed trigger times`() {
        val scheduledAt = Instant.parse("2026-06-10T09:00:00Z")
        val now = Instant.parse("2026-06-09T10:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders.map { it.remindType }).containsExactly(NotificationRemindType.D_DAY)
    }
}

package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationReminderCalculatorTest {

    private val calculator = NotificationReminderCalculator()

    @Test
    fun `futureReminders returns D-3 D-1 and D-Day at nine AM in Korea time`() {
        val scheduledAt = Instant.parse("2026-06-10T09:00:00Z")
        val now = Instant.parse("2026-06-01T00:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders.map { it.remindType }).containsExactly(
            NotificationRemindType.D_MINUS_3,
            NotificationRemindType.D_MINUS_1,
            NotificationRemindType.D_DAY,
        )
        assertThat(reminders.map { it.triggerAt }).containsExactly(
            Instant.parse("2026-06-07T00:00:00Z"),
            Instant.parse("2026-06-09T00:00:00Z"),
            Instant.parse("2026-06-10T00:00:00Z"),
        )
    }

    @Test
    fun `futureReminders skips stale reminders from previous dates`() {
        val scheduledAt = Instant.parse("2026-06-10T09:00:00Z")
        val now = Instant.parse("2026-06-08T01:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders.map { it.remindType }).containsExactly(
            NotificationRemindType.D_MINUS_1,
            NotificationRemindType.D_DAY,
        )
        assertThat(reminders.map { it.triggerAt }).containsExactly(
            Instant.parse("2026-06-09T00:00:00Z"),
            Instant.parse("2026-06-10T00:00:00Z"),
        )
    }

    @Test
    fun `futureReminders schedules today's missed reminder immediately before deadline`() {
        val scheduledAt = Instant.parse("2026-06-10T09:00:00Z")
        val now = Instant.parse("2026-06-10T01:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders.map { it.remindType }).containsExactly(NotificationRemindType.D_DAY)
        assertThat(reminders.single().triggerAt).isEqualTo(now)
    }

    @Test
    fun `futureReminders does not schedule reminders after deadline`() {
        val scheduledAt = Instant.parse("2026-06-10T09:00:00Z")
        val now = Instant.parse("2026-06-10T10:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders).isEmpty()
    }

    @Test
    fun `futureReminders uses deadline time when D-Day nine AM would be after deadline`() {
        val scheduledAt = Instant.parse("2026-06-09T22:00:00Z")
        val now = Instant.parse("2026-06-01T00:00:00Z")

        val reminders = calculator.futureReminders(scheduledAt, now)

        assertThat(reminders.last().remindType).isEqualTo(NotificationRemindType.D_DAY)
        assertThat(reminders.last().triggerAt).isEqualTo(scheduledAt)
    }
}

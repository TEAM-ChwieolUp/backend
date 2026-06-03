package com.cheerup.demo.notification.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.notification.domain.Notification
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import com.cheerup.demo.notification.repository.NotificationRepository
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationDueServiceTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var scheduleEventRepository: ScheduleEventRepository
    private lateinit var service: NotificationDueService

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk()
        applicationRepository = mockk()
        scheduleEventRepository = mockk()
        service = NotificationDueService(
            notificationRepository = notificationRepository,
            applicationRepository = applicationRepository,
            scheduleEventRepository = scheduleEventRepository,
            messageFactory = NotificationMessageFactory(),
        )
    }

    @Test
    fun `createNotification creates application deadline notification`() {
        val deadlineAt = Instant.parse("2026-06-10T09:00:00Z")
        val application = Application(
            userId = 99L,
            stageId = 1L,
            companyName = "Toss",
            position = "Backend",
            deadlineAt = deadlineAt,
            priority = Priority.NORMAL,
        )
        val savedSlot = slot<Notification>()

        every { applicationRepository.findByIdAndUserId(101L, 99L) } returns application
        every {
            notificationRepository.existsByUserIdAndSourceTypeAndSourceIdAndRemindTypeAndScheduledAt(
                userId = 99L,
                sourceType = NotificationSourceType.APPLICATION,
                sourceId = 101L,
                remindType = NotificationRemindType.D_MINUS_1,
                scheduledAt = deadlineAt,
            )
        } returns false
        every { notificationRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val removeFromQueue = service.createNotification("APPLICATION:101:D_MINUS_1:99")

        assertThat(removeFromQueue).isTrue()
        assertThat(savedSlot.captured.userId).isEqualTo(99L)
        assertThat(savedSlot.captured.sourceType).isEqualTo(NotificationSourceType.APPLICATION)
        assertThat(savedSlot.captured.sourceId).isEqualTo(101L)
        assertThat(savedSlot.captured.remindType).isEqualTo(NotificationRemindType.D_MINUS_1)
        assertThat(savedSlot.captured.scheduledAt).isEqualTo(deadlineAt)
        assertThat(savedSlot.captured.title).contains("Toss")
    }

    @Test
    fun `createNotification removes queue item without saving when source is missing`() {
        every { applicationRepository.findByIdAndUserId(101L, 99L) } returns null

        val removeFromQueue = service.createNotification("APPLICATION:101:D_DAY:99")

        assertThat(removeFromQueue).isTrue()
        verify(exactly = 0) {
            notificationRepository.existsByUserIdAndSourceTypeAndSourceIdAndRemindTypeAndScheduledAt(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        verify(exactly = 0) { notificationRepository.save(any()) }
    }

    @Test
    fun `createNotification creates notification when same remind type exists for different scheduledAt`() {
        val changedDeadlineAt = Instant.parse("2026-07-10T09:00:00Z")
        val application = Application(
            userId = 99L,
            stageId = 1L,
            companyName = "Toss",
            position = "Backend",
            deadlineAt = changedDeadlineAt,
            priority = Priority.NORMAL,
        )
        val savedSlot = slot<Notification>()

        every { applicationRepository.findByIdAndUserId(101L, 99L) } returns application
        every {
            notificationRepository.existsByUserIdAndSourceTypeAndSourceIdAndRemindTypeAndScheduledAt(
                userId = 99L,
                sourceType = NotificationSourceType.APPLICATION,
                sourceId = 101L,
                remindType = NotificationRemindType.D_MINUS_1,
                scheduledAt = changedDeadlineAt,
            )
        } returns false
        every { notificationRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val removeFromQueue = service.createNotification("APPLICATION:101:D_MINUS_1:99")

        assertThat(removeFromQueue).isTrue()
        assertThat(savedSlot.captured.scheduledAt).isEqualTo(changedDeadlineAt)
        verify(exactly = 1) { notificationRepository.save(any()) }
    }

    @Test
    fun `createNotification skips duplicate with same scheduledAt`() {
        val deadlineAt = Instant.parse("2026-06-10T09:00:00Z")
        val application = Application(
            userId = 99L,
            stageId = 1L,
            companyName = "Toss",
            position = "Backend",
            deadlineAt = deadlineAt,
            priority = Priority.NORMAL,
        )

        every { applicationRepository.findByIdAndUserId(101L, 99L) } returns application
        every {
            notificationRepository.existsByUserIdAndSourceTypeAndSourceIdAndRemindTypeAndScheduledAt(
                userId = 99L,
                sourceType = NotificationSourceType.APPLICATION,
                sourceId = 101L,
                remindType = NotificationRemindType.D_MINUS_1,
                scheduledAt = deadlineAt,
            )
        } returns true

        val removeFromQueue = service.createNotification("APPLICATION:101:D_MINUS_1:99")

        assertThat(removeFromQueue).isTrue()
        verify(exactly = 0) { notificationRepository.save(any()) }
    }
}

package com.cheerup.demo.schedule.service

import com.cheerup.demo.notification.service.NotificationQueue
import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class ScheduleSyncServiceTest {

    private lateinit var scheduleEventRepository: ScheduleEventRepository
    private lateinit var notificationQueue: NotificationQueue
    private lateinit var service: DefaultScheduleSyncService

    private val userId = 99L
    private val applicationId = 101L

    @BeforeEach
    fun setUp() {
        scheduleEventRepository = mockk(relaxUnitFun = true)
        notificationQueue = mockk(relaxUnitFun = true)
        service = DefaultScheduleSyncService(
            scheduleEventRepository = scheduleEventRepository,
            notificationQueue = notificationQueue,
        )
    }

    @Test
    fun `syncApplicationDeadline creates JOB_POSTING event without schedule notification`() {
        val deadlineAt = Instant.parse("2026-06-10T09:00:00Z")
        val savedSlot = slot<ScheduleEvent>()

        every {
            scheduleEventRepository.findByUserIdAndApplicationIdAndCategory(
                userId = userId,
                applicationId = applicationId,
                category = ScheduleCategory.JOB_POSTING,
            )
        } returns null
        every { scheduleEventRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        service.syncApplicationDeadline(userId, applicationId, "Toss", deadlineAt)

        assertThat(savedSlot.captured.userId).isEqualTo(userId)
        assertThat(savedSlot.captured.applicationId).isEqualTo(applicationId)
        assertThat(savedSlot.captured.category).isEqualTo(ScheduleCategory.JOB_POSTING)
        assertThat(savedSlot.captured.startAt).isEqualTo(deadlineAt)
        verify(exactly = 0) { notificationQueue.enqueueScheduleEvent(any(), any(), any()) }
        verify(exactly = 0) { notificationQueue.updateScheduleEvent(any(), any(), any()) }
    }

    @Test
    fun `syncApplicationDeadline updates JOB_POSTING event without schedule notification`() {
        val event = fixtureEvent(id = 501L, startAt = Instant.parse("2026-06-10T09:00:00Z"))
        val nextDeadlineAt = Instant.parse("2026-07-10T09:00:00Z")

        every {
            scheduleEventRepository.findByUserIdAndApplicationIdAndCategory(
                userId = userId,
                applicationId = applicationId,
                category = ScheduleCategory.JOB_POSTING,
            )
        } returns event

        service.syncApplicationDeadline(userId, applicationId, "Toss", nextDeadlineAt)

        assertThat(event.startAt).isEqualTo(nextDeadlineAt)
        verify(exactly = 0) { notificationQueue.enqueueScheduleEvent(any(), any(), any()) }
        verify(exactly = 0) { notificationQueue.updateScheduleEvent(any(), any(), any()) }
    }

    @Test
    fun `syncApplicationDeadline removes stale schedule notification when deadline is cleared`() {
        val event = fixtureEvent(id = 501L, startAt = Instant.parse("2026-06-10T09:00:00Z"))

        every {
            scheduleEventRepository.findByUserIdAndApplicationIdAndCategory(
                userId = userId,
                applicationId = applicationId,
                category = ScheduleCategory.JOB_POSTING,
            )
        } returns event

        service.syncApplicationDeadline(userId, applicationId, "Toss", null)

        verify(exactly = 1) { notificationQueue.removeByEventId(userId, 501L) }
        verify(exactly = 1) { scheduleEventRepository.delete(event) }
    }

    @Test
    fun `deleteByApplicationId removes stale schedule notifications before deleting events`() {
        val first = fixtureEvent(id = 501L, startAt = Instant.parse("2026-06-10T09:00:00Z"))
        val second = fixtureEvent(
            id = 502L,
            category = ScheduleCategory.APPLICATION_PROCESS,
            startAt = Instant.parse("2026-06-12T09:00:00Z"),
        )

        every { scheduleEventRepository.findAllByUserIdAndApplicationId(userId, applicationId) } returns
            listOf(first, second)

        service.deleteByApplicationId(userId, applicationId)

        verify(exactly = 1) { notificationQueue.removeByEventId(userId, 501L) }
        verify(exactly = 1) { notificationQueue.removeByEventId(userId, 502L) }
        verify(exactly = 1) { scheduleEventRepository.deleteAll(listOf(first, second)) }
    }

    private fun fixtureEvent(
        id: Long,
        category: ScheduleCategory = ScheduleCategory.JOB_POSTING,
        startAt: Instant,
    ): ScheduleEvent {
        val event = ScheduleEvent(
            userId = userId,
            applicationId = applicationId,
            category = category,
            title = "Toss 채용 마감",
            startAt = startAt,
            endAt = null,
        )
        ReflectionTestUtils.setField(event, "id", id)
        return event
    }
}

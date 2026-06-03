package com.cheerup.demo.schedule.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.notification.service.NotificationQueue
import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import com.cheerup.demo.schedule.dto.CreateScheduleEventRequest
import com.cheerup.demo.schedule.dto.UpdateScheduleEventRequest
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class ScheduleEventCommandServiceTest {

    private lateinit var scheduleEventRepository: ScheduleEventRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var notificationQueue: NotificationQueue
    private lateinit var service: ScheduleEventCommandService

    private val userId = 99L
    private val eventId = 501L

    @BeforeEach
    fun setUp() {
        scheduleEventRepository = mockk(relaxUnitFun = true)
        applicationRepository = mockk()
        notificationQueue = mockk(relaxUnitFun = true)
        service = ScheduleEventCommandService(
            scheduleEventRepository = scheduleEventRepository,
            applicationRepository = applicationRepository,
            iCalendarBuilder = ICalendarBuilder(),
            notificationQueue = notificationQueue,
        )
    }

    @Test
    fun `create enqueues schedule event reminder after saving direct schedule`() {
        val startAt = Instant.parse("2026-06-10T09:00:00Z")

        every { scheduleEventRepository.save(any<ScheduleEvent>()) } answers {
            firstArg<ScheduleEvent>().also {
                ReflectionTestUtils.setField(it, "id", eventId)
            }
        }

        val response = service.create(
            userId = userId,
            request = CreateScheduleEventRequest(
                category = ScheduleCategory.PERSONAL,
                title = "Study",
                startAt = startAt,
            ),
        )

        assertThat(response.id).isEqualTo(eventId)
        verify(exactly = 1) {
            notificationQueue.enqueueScheduleEvent(
                userId = userId,
                eventId = eventId,
                startAt = startAt,
            )
        }
    }

    @Test
    fun `update enqueues schedule event update when startAt changes`() {
        val event = fixtureEvent(startAt = Instant.parse("2026-06-10T09:00:00Z"))
        val nextStartAt = Instant.parse("2026-06-12T09:00:00Z")

        every { scheduleEventRepository.findByIdAndUserId(eventId, userId) } returns event

        service.update(
            userId = userId,
            eventId = eventId,
            request = UpdateScheduleEventRequest(startAt = nextStartAt),
        )

        assertThat(event.startAt).isEqualTo(nextStartAt)
        verify(exactly = 1) {
            notificationQueue.updateScheduleEvent(
                userId = userId,
                eventId = eventId,
                startAt = nextStartAt,
            )
        }
    }

    @Test
    fun `update does not touch notification queue when startAt is unchanged`() {
        val event = fixtureEvent(startAt = Instant.parse("2026-06-10T09:00:00Z"))

        every { scheduleEventRepository.findByIdAndUserId(eventId, userId) } returns event

        service.update(
            userId = userId,
            eventId = eventId,
            request = UpdateScheduleEventRequest(title = "Updated"),
        )

        assertThat(event.title).isEqualTo("Updated")
        verify(exactly = 0) {
            notificationQueue.updateScheduleEvent(any(), any(), any())
        }
    }

    @Test
    fun `delete removes schedule event reminder before deleting event`() {
        val event = fixtureEvent(startAt = Instant.parse("2026-06-10T09:00:00Z"))

        every { scheduleEventRepository.findByIdAndUserId(eventId, userId) } returns event

        service.delete(userId = userId, eventId = eventId)

        verifyOrder {
            notificationQueue.removeByEventId(userId, eventId)
            scheduleEventRepository.delete(event)
        }
    }

    private fun fixtureEvent(startAt: Instant): ScheduleEvent {
        val event = ScheduleEvent(
            userId = userId,
            applicationId = null,
            category = ScheduleCategory.PERSONAL,
            title = "Study",
            startAt = startAt,
            endAt = null,
        )
        ReflectionTestUtils.setField(event, "id", eventId)
        return event
    }
}

package com.cheerup.demo.schedule.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.notification.service.NotificationQueue
import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import com.cheerup.demo.schedule.dto.CreateScheduleEventRequest
import com.cheerup.demo.schedule.dto.UpdateScheduleEventRequest
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class ScheduleEventCommandServiceTest {

    private lateinit var scheduleEventRepository: ScheduleEventRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var notificationQueue: NotificationQueue
    private lateinit var service: ScheduleEventCommandService

    private val userId = 99L
    private val eventId = 501L
    private val applicationId = 101L

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
    fun `create enqueues schedule event reminder by endAt when present`() {
        val startAt = Instant.parse("2026-06-10T10:00:00Z")
        val endAt = Instant.parse("2026-06-10T18:00:00Z")

        every { scheduleEventRepository.save(any<ScheduleEvent>()) } answers {
            firstArg<ScheduleEvent>().also {
                ReflectionTestUtils.setField(it, "id", eventId)
            }
        }

        service.create(
            userId = userId,
            request = CreateScheduleEventRequest(
                category = ScheduleCategory.PERSONAL,
                title = "Study",
                startAt = startAt,
                endAt = endAt,
            ),
        )

        verify(exactly = 1) {
            notificationQueue.enqueueScheduleEvent(
                userId = userId,
                eventId = eventId,
                startAt = endAt,
            )
        }
    }

    @Test
    fun `create allows JOB_POSTING without applicationId`() {
        val startAt = Instant.parse("2026-06-10T09:00:00Z")
        val savedSlot = slot<ScheduleEvent>()

        every { scheduleEventRepository.save(capture(savedSlot)) } answers {
            savedSlot.captured.also {
                ReflectionTestUtils.setField(it, "id", eventId)
            }
        }

        val response = service.create(
            userId = userId,
            request = CreateScheduleEventRequest(
                category = ScheduleCategory.JOB_POSTING,
                title = "Toss 채용 설명회",
                startAt = startAt,
            ),
        )

        assertThat(response.id).isEqualTo(eventId)
        assertThat(response.applicationId).isNull()
        assertThat(response.category).isEqualTo(ScheduleCategory.JOB_POSTING)
        assertThat(savedSlot.captured.applicationId).isNull()
        verify(exactly = 0) { applicationRepository.findByIdAndUserId(any(), any()) }
        verify(exactly = 0) {
            scheduleEventRepository.existsByUserIdAndApplicationIdAndCategory(any(), any(), any())
        }
    }

    @Test
    fun `create validates duplicate only when JOB_POSTING has applicationId`() {
        val startAt = Instant.parse("2026-06-10T09:00:00Z")
        val savedSlot = slot<ScheduleEvent>()

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every {
            scheduleEventRepository.existsByUserIdAndApplicationIdAndCategory(
                userId = userId,
                applicationId = applicationId,
                category = ScheduleCategory.JOB_POSTING,
            )
        } returns false
        every { scheduleEventRepository.save(capture(savedSlot)) } answers {
            savedSlot.captured.also {
                ReflectionTestUtils.setField(it, "id", eventId)
            }
        }

        val response = service.create(
            userId = userId,
            request = CreateScheduleEventRequest(
                category = ScheduleCategory.JOB_POSTING,
                applicationId = applicationId,
                title = "Toss 채용 마감",
                startAt = startAt,
            ),
        )

        assertThat(response.applicationId).isEqualTo(applicationId)
        verify(exactly = 1) { applicationRepository.findByIdAndUserId(applicationId, userId) }
        verify(exactly = 1) {
            scheduleEventRepository.existsByUserIdAndApplicationIdAndCategory(
                userId = userId,
                applicationId = applicationId,
                category = ScheduleCategory.JOB_POSTING,
            )
        }
    }

    @Test
    fun `create still requires applicationId for APPLICATION_PROCESS`() {
        val startAt = Instant.parse("2026-06-10T09:00:00Z")

        val exception = assertThrows<BusinessException> {
            service.create(
                userId = userId,
                request = CreateScheduleEventRequest(
                    category = ScheduleCategory.APPLICATION_PROCESS,
                    title = "1차 면접",
                    startAt = startAt,
                ),
            )
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT)
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
    fun `update enqueues schedule event update when endAt changes notification time`() {
        val event = fixtureEvent(
            startAt = Instant.parse("2026-06-10T09:00:00Z"),
            endAt = Instant.parse("2026-06-10T10:00:00Z"),
        )
        val nextEndAt = Instant.parse("2026-06-10T18:00:00Z")

        every { scheduleEventRepository.findByIdAndUserId(eventId, userId) } returns event

        service.update(
            userId = userId,
            eventId = eventId,
            request = UpdateScheduleEventRequest(endAt = nextEndAt),
        )

        assertThat(event.endAt).isEqualTo(nextEndAt)
        verify(exactly = 1) {
            notificationQueue.updateScheduleEvent(
                userId = userId,
                eventId = eventId,
                startAt = nextEndAt,
            )
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

    private fun fixtureEvent(
        startAt: Instant,
        endAt: Instant? = null,
    ): ScheduleEvent {
        val event = ScheduleEvent(
            userId = userId,
            applicationId = null,
            category = ScheduleCategory.PERSONAL,
            title = "Study",
            startAt = startAt,
            endAt = endAt,
        )
        ReflectionTestUtils.setField(event, "id", eventId)
        return event
    }

    private fun fixtureApplication(): Application =
        Application(
            userId = userId,
            stageId = 1L,
            companyName = "Toss",
            position = "Backend",
        )
}

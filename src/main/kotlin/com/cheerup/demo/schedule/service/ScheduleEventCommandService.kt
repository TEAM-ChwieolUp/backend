package com.cheerup.demo.schedule.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.notification.service.NoOpNotificationQueue
import com.cheerup.demo.notification.service.NotificationQueue
import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import com.cheerup.demo.schedule.domain.ScheduleEventOrigin
import com.cheerup.demo.schedule.dto.CreateScheduleEventRequest
import com.cheerup.demo.schedule.dto.ScheduleEventResponse
import com.cheerup.demo.schedule.dto.UpdateScheduleEventRequest
import com.cheerup.demo.schedule.dto.toScheduleEventResponse
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ScheduleEventCommandService(
    private val scheduleEventRepository: ScheduleEventRepository,
    private val applicationRepository: ApplicationRepository,
    private val iCalendarBuilder: ICalendarBuilder,
    private val notificationQueue: NotificationQueue = NoOpNotificationQueue,
) {

    @Transactional
    fun create(
        userId: Long,
        request: CreateScheduleEventRequest,
    ): ScheduleEventResponse {
        validateEndAt(request.startAt, request.endAt)
        validateApplicationReference(userId, request.category, request.applicationId)

        if (request.category == ScheduleCategory.JOB_POSTING &&
            request.applicationId != null &&
            scheduleEventRepository.existsByUserIdAndApplicationIdAndCategory(
                userId = userId,
                applicationId = request.applicationId,
                category = ScheduleCategory.JOB_POSTING,
            )
        ) {
            throw BusinessException(
                ErrorCode.SCHEDULE_DUPLICATE_JOB_POSTING,
                detail = "applicationId=${request.applicationId}",
            )
        }

        val event = ScheduleEvent(
            userId = userId,
            applicationId = request.applicationId,
            category = request.category,
            title = request.title,
            startAt = request.startAt,
            endAt = request.endAt,
        )

        val saved = scheduleEventRepository.save(event)
        val eventId = requireNotNull(saved.id) { "ScheduleEvent must be persisted" }
        notificationQueue.enqueueScheduleEvent(
            userId = userId,
            eventId = eventId,
            startAt = saved.notificationAt(),
        )

        return saved.toScheduleEventResponse()
    }

    @Transactional
    fun update(
        userId: Long,
        eventId: Long,
        request: UpdateScheduleEventRequest,
    ): ScheduleEventResponse {
        val event = findOwnedEvent(userId, eventId)
        val previousNotificationAt = event.notificationAt()

        val nextStartAt = request.startAt ?: event.startAt
        val nextEndAt = request.endAt ?: event.endAt
        validateEndAt(nextStartAt, nextEndAt)

        request.title?.let { event.title = it }
        if (request.startAt != null || request.endAt != null) {
            event.reschedule(nextStartAt, nextEndAt)
        }
        if (event.notificationAt() != previousNotificationAt) {
            notificationQueue.updateScheduleEvent(
                userId = userId,
                eventId = eventId,
                startAt = event.notificationAt(),
            )
        }

        return event.toScheduleEventResponse()
    }

    @Transactional
    fun delete(userId: Long, eventId: Long) {
        val event = findOwnedEvent(userId, eventId)

        if (event.origin == ScheduleEventOrigin.APPLICATION_DEADLINE && hasLinkedApplicationDeadline(userId, event)) {
            throw BusinessException(
                ErrorCode.SCHEDULE_APPLICATION_DEADLINE_LOCKED,
                detail = "eventId=$eventId, applicationId=${event.applicationId}",
            )
        }

        notificationQueue.removeByEventId(userId, eventId)
        scheduleEventRepository.delete(event)
    }

    fun export(userId: Long, eventId: Long): String =
        iCalendarBuilder.build(findOwnedEvent(userId, eventId))

    private fun findOwnedEvent(userId: Long, eventId: Long): ScheduleEvent =
        scheduleEventRepository.findByIdAndUserId(eventId, userId)
            ?: throw BusinessException(ErrorCode.SCHEDULE_NOT_FOUND, detail = "eventId=$eventId")

    private fun validateApplicationReference(
        userId: Long,
        category: ScheduleCategory,
        applicationId: Long?,
    ) {
        when (category) {
            ScheduleCategory.PERSONAL -> {
                if (applicationId != null) {
                    invalidInput("PERSONAL schedule must not have applicationId")
                }
            }

            ScheduleCategory.JOB_POSTING -> {
                if (applicationId != null) {
                    applicationRepository.findByIdAndUserId(applicationId, userId)
                        ?: throw BusinessException(ErrorCode.APPLICATION_NOT_FOUND, detail = "applicationId=$applicationId")
                }
            }

            ScheduleCategory.APPLICATION_PROCESS -> {
                if (applicationId == null) {
                    invalidInput("$category schedule requires applicationId")
                }

                applicationRepository.findByIdAndUserId(applicationId, userId)
                    ?: throw BusinessException(ErrorCode.APPLICATION_NOT_FOUND, detail = "applicationId=$applicationId")
            }
        }
    }

    private fun hasLinkedApplicationDeadline(userId: Long, event: ScheduleEvent): Boolean {
        val applicationId = event.applicationId ?: return false
        return applicationRepository.findByIdAndUserId(applicationId, userId)?.deadlineAt != null
    }

    private fun validateEndAt(startAt: Instant, endAt: Instant?) {
        if (endAt != null && endAt.isBefore(startAt)) {
            invalidInput("endAt must be equal to or after startAt")
        }
    }

    private fun invalidInput(detail: String): Nothing =
        throw BusinessException(ErrorCode.INVALID_INPUT, detail = detail)
}

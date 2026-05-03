package com.cheerup.demo.schedule.dto

import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import java.time.Instant

data class CalendarResponse(
    val events: List<ScheduleEventResponse>,
)

data class ScheduleEventResponse(
    val id: Long,
    val applicationId: Long?,
    val category: ScheduleCategory,
    val title: String,
    val startAt: Instant,
    val endAt: Instant?,
)

typealias CalendarEventResponse = ScheduleEventResponse

fun ScheduleEvent.toScheduleEventResponse(): ScheduleEventResponse =
    ScheduleEventResponse(
        id = requireNotNull(id) { "ScheduleEvent must be persisted" },
        applicationId = applicationId,
        category = category,
        title = title,
        startAt = startAt,
        endAt = endAt,
    )

fun ScheduleEvent.toCalendarEventResponse(): CalendarEventResponse =
    toScheduleEventResponse()

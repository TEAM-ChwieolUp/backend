package com.cheerup.demo.schedule.dto

import com.cheerup.demo.schedule.domain.ScheduleCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateScheduleEventRequest(
    val category: ScheduleCategory,
    val applicationId: Long? = null,

    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    val startAt: Instant,
    val endAt: Instant? = null,
)

data class UpdateScheduleEventRequest(
    // category/applicationId are immutable on PATCH; accepted only to ignore accidental client input.
    val category: ScheduleCategory? = null,
    val applicationId: Long? = null,

    @field:NotBlank
    @field:Size(max = 200)
    val title: String? = null,

    val startAt: Instant? = null,
    val endAt: Instant? = null,
)

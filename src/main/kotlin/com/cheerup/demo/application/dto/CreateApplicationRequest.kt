package com.cheerup.demo.application.dto

import com.cheerup.demo.application.domain.Priority
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateApplicationRequest(
    val stageId: Long,

    @field:NotBlank
    @field:Size(max = 100)
    val companyName: String,

    @field:NotBlank
    @field:Size(max = 100)
    val position: String,

    val appliedAt: Instant? = null,
    val deadlineAt: Instant? = null,

    @field:Min(0)
    val noResponseDays: Int? = 7,

    val priority: Priority = Priority.NORMAL,

    val memo: String? = null,

    @field:Size(max = 2048)
    val jobPostingUrl: String? = null,

    val tagIds: List<Long> = emptyList(),
)

package com.cheerup.demo.application.dto

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import java.time.Instant

/**
 * 단일 카드 응답 DTO. PATCH 결과를 그대로 활용할 수 있도록
 * [BoardResponse]의 [ApplicationCard]와 달리 `stageId`도 포함한다.
 */
data class ApplicationResponse(
    val id: Long,
    val stageId: Long,
    val companyName: String,
    val position: String,
    val appliedAt: Instant?,
    val deadlineAt: Instant?,
    val noResponseDays: Int?,
    val priority: Priority,
    val memo: String?,
    val jobPostingUrl: String?,
    val tags: List<TagSummary>,
)

fun Application.toResponse(tags: List<TagSummary>): ApplicationResponse =
    ApplicationResponse(
        id = requireNotNull(id) { "Application must be persisted" },
        stageId = stageId,
        companyName = companyName,
        position = position,
        appliedAt = appliedAt,
        deadlineAt = deadlineAt,
        noResponseDays = noResponseDays,
        priority = priority,
        memo = memo,
        jobPostingUrl = jobPostingUrl,
        tags = tags,
    )

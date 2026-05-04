package com.cheerup.demo.application.dto

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import java.time.Instant

data class BoardResponse(
    val stages: List<StageNode>,
)

data class StageNode(
    val id: Long,
    val name: String,
    val displayOrder: Int,
    val color: String,
    val category: StageCategory,
    val applications: List<ApplicationCard>,
)

data class ApplicationCard(
    val id: Long,
    val companyName: String,
    val position: String,
    val appliedAt: Instant?,
    val deadlineAt: Instant?,
    val priority: Priority,
    val memo: String?,
    val jobPostingUrl: String?,
    val tags: List<TagSummary>,
)

data class TagSummary(
    val id: Long,
    val name: String,
    val color: String,
)

fun Stage.toNode(applications: List<ApplicationCard>): StageNode =
    StageNode(
        id = requireNotNull(id) { "Stage must be persisted" },
        name = name,
        displayOrder = displayOrder,
        color = color,
        category = category,
        applications = applications,
    )

fun Application.toCard(tags: List<TagSummary>): ApplicationCard =
    ApplicationCard(
        id = requireNotNull(id) { "Application must be persisted" },
        companyName = companyName,
        position = position,
        appliedAt = appliedAt,
        deadlineAt = deadlineAt,
        priority = priority,
        memo = memo,
        jobPostingUrl = jobPostingUrl,
        tags = tags,
    )

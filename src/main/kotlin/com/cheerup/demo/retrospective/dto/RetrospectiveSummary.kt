package com.cheerup.demo.retrospective.dto

import com.cheerup.demo.retrospective.domain.Retrospective
import java.time.Instant

data class RetrospectiveSummary(
    val id: Long,
    val applicationId: Long,
    val stageId: Long?,
    val itemCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RetrospectiveListResponse(
    val retrospectives: List<RetrospectiveSummary>,
)

fun Retrospective.toSummary(): RetrospectiveSummary =
    RetrospectiveSummary(
        id = requireNotNull(id) { "Retrospective must be persisted" },
        applicationId = applicationId,
        stageId = stageId,
        itemCount = items.size,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

package com.cheerup.demo.retrospective.dto

import com.cheerup.demo.retrospective.domain.Retrospective
import com.cheerup.demo.retrospective.domain.RetrospectiveItem
import java.time.Instant

data class RetrospectiveItemResponse(
    val question: String,
    val answer: String?,
)

data class RetrospectiveResponse(
    val id: Long,
    val applicationId: Long,
    val stageId: Long?,
    val items: List<RetrospectiveItemResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun RetrospectiveItem.toResponse(): RetrospectiveItemResponse =
    RetrospectiveItemResponse(question = question, answer = answer)

fun Retrospective.toResponse(): RetrospectiveResponse =
    RetrospectiveResponse(
        id = requireNotNull(id) { "Retrospective must be persisted" },
        applicationId = applicationId,
        stageId = stageId,
        items = items.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

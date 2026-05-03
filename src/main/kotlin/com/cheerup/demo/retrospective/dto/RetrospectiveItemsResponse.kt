package com.cheerup.demo.retrospective.dto

import com.cheerup.demo.retrospective.domain.Retrospective

data class RetrospectiveItemsResponse(
    val items: List<RetrospectiveItemResponse>,
    val version: Long,
)

fun Retrospective.toItemsResponse(): RetrospectiveItemsResponse =
    RetrospectiveItemsResponse(
        items = items.map { it.toResponse() },
        version = version,
    )

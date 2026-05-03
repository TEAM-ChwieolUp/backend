package com.cheerup.demo.application.dto

import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory

data class StageResponse(
    val id: Long,
    val name: String,
    val displayOrder: Int,
    val color: String,
    val category: StageCategory,
)

fun Stage.toStageResponse(): StageResponse =
    StageResponse(
        id = requireNotNull(id) { "Stage must be persisted" },
        name = name,
        displayOrder = displayOrder,
        color = color,
        category = category,
    )

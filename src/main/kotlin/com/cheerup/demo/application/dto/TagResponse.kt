package com.cheerup.demo.application.dto

import com.cheerup.demo.application.domain.Tag

data class TagResponse(
    val id: Long,
    val name: String,
    val color: String,
)

fun Tag.toTagResponse(): TagResponse =
    TagResponse(
        id = requireNotNull(id) { "Tag must be persisted" },
        name = name,
        color = color,
    )

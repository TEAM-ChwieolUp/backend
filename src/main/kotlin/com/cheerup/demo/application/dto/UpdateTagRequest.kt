package com.cheerup.demo.application.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * PATCH /api/tags/{id} 요청 DTO.
 *
 * 모든 필드 옵셔널, null이면 "변경 없음".
 */
data class UpdateTagRequest(
    @field:Size(min = 1, max = 30)
    val name: String? = null,

    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a 6-digit hex code (e.g. #4F46E5)")
    val color: String? = null,
) {
    fun isEmpty(): Boolean = name == null && color == null
}

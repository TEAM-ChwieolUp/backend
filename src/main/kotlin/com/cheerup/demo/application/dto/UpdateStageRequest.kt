package com.cheerup.demo.application.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/**
 * PATCH /api/stages/{id} 요청 DTO.
 *
 * 모든 필드는 옵셔널이며, null이면 "변경 없음"을 의미한다.
 * `category`는 시스템 관리 항목이라 본 요청으로 변경할 수 없다 (Stage.category가 val).
 */
data class UpdateStageRequest(
    @field:Size(min = 1, max = 30)
    val name: String? = null,

    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a 6-digit hex code (e.g. #4F46E5)")
    val color: String? = null,

    @field:PositiveOrZero
    val displayOrder: Int? = null,
) {
    fun isEmpty(): Boolean = name == null && color == null && displayOrder == null
}

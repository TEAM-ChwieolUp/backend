package com.cheerup.demo.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/**
 * POST /api/stages 요청 DTO.
 *
 * `category`는 항상 서버에서 `IN_PROGRESS`로 자동 설정된다 (사용자 입력 불가).
 * `displayOrder`가 null이면 사용자의 마지막 Stage 다음 순서로 자동 부여된다.
 */
data class CreateStageRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    @field:NotBlank
    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a 6-digit hex code (e.g. #4F46E5)")
    val color: String,

    @field:PositiveOrZero
    val displayOrder: Int? = null,
)

package com.cheerup.demo.application.dto

import com.cheerup.demo.application.domain.Priority
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * PATCH /api/applications/{id} 요청 DTO.
 *
 * 모든 필드는 옵셔널이며, null이면 "변경 없음"을 의미한다.
 * 단, [tagIds]는 예외:
 * - `null` → 태그 변경 없음
 * - `emptyList()` → 모든 태그 제거
 *
 * 빈 본문(`{}`)은 어떤 변경도 일으키지 않고 200 응답을 반환한다.
 */
data class UpdateApplicationRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val companyName: String? = null,

    @field:NotBlank
    @field:Size(min = 1, max = 100)
    val position: String? = null,

    val stageId: Long? = null,

    val appliedAt: Instant? = null,

    val deadlineAt: Instant? = null,

    @field:Min(1)
    @field:Max(365)
    val noResponseDays: Int? = null,

    val priority: Priority? = null,

    @field:Size(max = 5000)
    val memo: String? = null,

    @field:Size(max = 2048)
    val jobPostingUrl: String? = null,

    val tagIds: List<Long>? = null,
) {
    fun isEmpty(): Boolean = listOf(
        companyName, position, stageId, appliedAt, deadlineAt,
        noResponseDays, priority, memo, jobPostingUrl, tagIds,
    ).all { it == null }
}

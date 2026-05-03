package com.cheerup.demo.application.api

import com.cheerup.demo.application.dto.CreateStageRequest
import com.cheerup.demo.application.dto.StageResponse
import com.cheerup.demo.application.dto.UpdateStageRequest
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Stages", description = "칸반 보드 Stage(컬럼) API")
interface StageApi {

    @Operation(
        summary = "Stage 목록 조회",
        description = "사용자가 소유한 Stage를 displayOrder 오름차순으로 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        ],
    )
    fun list(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<List<StageResponse>>

    @Operation(
        summary = "Stage 생성",
        description = "새 Stage를 생성합니다. category는 항상 IN_PROGRESS로 자동 설정되며 사용자가 지정할 수 없습니다. " +
            "displayOrder를 생략하면 사용자의 마지막 Stage 다음 순서로 자동 부여됩니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: Long,
        request: CreateStageRequest,
    ): ResponseEntity<ApiResponse<StageResponse>>

    @Operation(
        summary = "Stage 수정",
        description = "Stage의 name/color/displayOrder를 부분 수정합니다. " +
            "category는 시스템 관리 항목이라 변경할 수 없습니다. null 필드는 변경하지 않습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.STAGE_ORDER_PROTECTED),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 Stage ID", example = "2") id: Long,
        request: UpdateStageRequest,
    ): ApiResponse<StageResponse>

    @Operation(
        summary = "Stage 삭제",
        description = "Stage를 삭제합니다. (1) category가 PASSED/REJECTED가 아닐 것, " +
            "(2) 해당 Stage에 카드가 0개일 것을 모두 만족해야 합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.STAGE_FIXED),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_EMPTY),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 Stage ID", example = "2") id: Long,
    ): ResponseEntity<Void>
}

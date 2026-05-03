package com.cheerup.demo.application.api

import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.dto.ApplicationCard
import com.cheerup.demo.application.dto.ApplicationResponse
import com.cheerup.demo.application.dto.BoardResponse
import com.cheerup.demo.application.dto.CreateApplicationRequest
import com.cheerup.demo.application.dto.UpdateApplicationRequest
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Applications", description = "채용 칸반 보드 카드 API")
interface ApplicationApi {

    @Operation(
        summary = "채용 칸반 보드 조회",
        description = "Stage 목록과 각 Stage에 속한 채용 카드를 조회합니다. " +
            "stage, tag, priority 조건으로 필터링할 수 있습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
        ],
    )
    fun getBoard(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "특정 Stage ID에 속한 카드만 조회", example = "2") stage: Long?,
        @Parameter(description = "특정 Tag ID가 연결된 카드만 조회", example = "5") tag: Long?,
        @Parameter(description = "우선순위 필터 (LOW/NORMAL/HIGH)", example = "HIGH") priority: Priority?,
    ): ApiResponse<BoardResponse>

    @Operation(
        summary = "채용 카드 생성",
        description = "새 채용 카드를 생성합니다. stageId와 tagIds는 요청 사용자가 소유한 리소스여야 합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.TAG_NOT_FOUND),
        ],
    )
    fun createApplication(
        @Parameter(hidden = true) userId: Long,
        request: CreateApplicationRequest,
    ): ResponseEntity<ApiResponse<ApplicationCard>>

    @Operation(
        summary = "채용 카드 수정",
        description = "채용 카드의 필드를 부분 수정합니다. null 필드는 변경하지 않으며, " +
            "tagIds가 빈 배열이면 모든 태그를 제거합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.TAG_NOT_FOUND),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 채용 카드 ID", example = "101") id: Long,
        request: UpdateApplicationRequest,
    ): ApiResponse<ApplicationResponse>

    @Operation(
        summary = "채용 카드 삭제",
        description = "채용 카드를 삭제합니다. 연결된 태그(application_tags)는 함께 정리됩니다. " +
            "본인 소유가 아닌 카드는 404로 응답합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 채용 카드 ID", example = "101") id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "회고 생성 (빈 회고)",
        description = "지정한 채용 카드에 빈 회고를 생성합니다. stageId가 있으면 단계별 회고, " +
            "없으면 카드 종합 회고가 됩니다. 라우팅만 본 도메인이 받고 본문은 retrospective/ 도메인이 처리합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
        ],
    )
    fun createRetrospective(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "회고를 생성할 채용 카드 ID", example = "101") id: Long,
        request: CreateRetrospectiveRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveResponse>>
}

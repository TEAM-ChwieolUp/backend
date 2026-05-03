package com.cheerup.demo.retrospective.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.AddRetrospectiveItemRequest
import com.cheerup.demo.retrospective.dto.ApplyRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveItemsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveListResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveItemRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Retrospectives", description = "회고 조회 API")
interface RetrospectiveApi {

    @Operation(
        summary = "카드별 회고 목록 조회",
        description = "특정 채용 카드에 작성된 회고 목록을 조회합니다. " +
            "응답에는 항목 본문(items)이 포함되지 않으며 itemCount만 노출됩니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
        ],
    )
    fun listByApplication(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회할 채용 카드 ID", example = "101") appId: Long,
    ): ApiResponse<RetrospectiveListResponse>

    @Operation(
        summary = "회고 단건 조회",
        description = "회고 단건을 항목 본문(items) 포함하여 조회합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_NOT_FOUND),
        ],
    )
    fun getOne(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회할 회고 ID", example = "12") id: Long,
    ): ApiResponse<RetrospectiveResponse>

    @Operation(
        summary = "회고 삭제",
        description = "회고를 hard delete 합니다. 외부 시스템 정리는 없습니다. " +
            "본인 소유가 아닌 회고는 404로 응답합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_NOT_FOUND),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 회고 ID", example = "12") id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "회고 항목 추가",
        description = "회고에 질문-답변 쌍을 1건 append 합니다. " +
            "응답은 변경 후 전체 items와 @Version 값을 함께 반환합니다. " +
            "동시 변경 충돌 시 1회 자동 재시도되며, 두 번째도 실패하면 409 RETROSPECTIVE_CONCURRENT_MODIFICATION으로 응답합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION),
        ],
    )
    fun addItem(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "항목을 추가할 회고 ID", example = "12") id: Long,
        request: AddRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "회고 항목 수정",
        description = "인덱스 위치 항목의 question/answer를 부분 수정합니다. " +
            "빈 본문(`{}`)은 변경 없이 200으로 응답합니다. " +
            "인덱스가 범위를 벗어나면 404 RETROSPECTIVE_ITEM_INDEX_INVALID로 응답합니다. " +
            "동시 변경 충돌 시 1회 자동 재시도되며, 두 번째도 실패하면 409 RETROSPECTIVE_CONCURRENT_MODIFICATION으로 응답합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_ITEM_INDEX_INVALID),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION),
        ],
    )
    fun updateItem(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "회고 ID", example = "12") id: Long,
        @Parameter(description = "수정할 항목의 0-base 인덱스", example = "0") index: Int,
        request: UpdateRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "회고 항목 삭제",
        description = "인덱스 위치 항목을 제거합니다. 이후 항목들의 인덱스는 -1씩 당겨집니다. " +
            "인덱스가 범위를 벗어나면 404 RETROSPECTIVE_ITEM_INDEX_INVALID로 응답합니다. " +
            "동시 변경 충돌 시 1회 자동 재시도되며, 두 번째도 실패하면 409 RETROSPECTIVE_CONCURRENT_MODIFICATION으로 응답합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_ITEM_INDEX_INVALID),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION),
        ],
    )
    fun deleteItem(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "회고 ID", example = "12") id: Long,
        @Parameter(description = "삭제할 항목의 0-base 인덱스", example = "0") index: Int,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "AI 회고 질문 생성",
        description = "채용 카드와 선택 단계 컨텍스트를 기반으로 회고 질문 목록을 생성합니다. " +
            "결과는 DB에 저장하지 않습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.RATE_LIMITED),
            SwaggerErrorResponse(ErrorCode.AI_GENERATION_FAILED),
            SwaggerErrorResponse(ErrorCode.AI_GENERATION_TIMEOUT),
        ],
    )
    fun generateAiQuestions(
        @Parameter(hidden = true) userId: Long,
        request: RetrospectiveQuestionRequest,
    ): ApiResponse<RetrospectiveQuestionsResponse>

    @Operation(
        summary = "회고 템플릿 적용",
        description = "선택한 템플릿의 질문들을 회고 items 끝에 append 합니다. " +
            "답변은 null로 초기화되며, 이후 일반 항목 조작으로 채워 넣습니다. " +
            "템플릿/회고 모두 요청자 소유여야 합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND),
        ],
    )
    fun applyTemplate(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "템플릿을 적용할 회고 ID", example = "12") id: Long,
        request: ApplyRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveResponse>
}

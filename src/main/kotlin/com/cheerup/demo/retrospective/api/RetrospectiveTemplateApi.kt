package com.cheerup.demo.retrospective.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveTemplateResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveTemplateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Retrospective Templates", description = "회고에 적용할 질문 묶음 템플릿 API")
interface RetrospectiveTemplateApi {

    @Operation(
        summary = "회고 템플릿 목록 조회",
        description = "사용자가 소유한 회고 질문 템플릿 전체를 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        ],
    )
    fun list(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<List<RetrospectiveTemplateResponse>>

    @Operation(
        summary = "회고 템플릿 단건 조회",
        description = "템플릿 단건을 questions 포함하여 조회합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND),
        ],
    )
    fun get(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회할 템플릿 ID", example = "3") id: Long,
    ): ApiResponse<RetrospectiveTemplateResponse>

    @Operation(
        summary = "회고 템플릿 생성",
        description = "새 템플릿을 생성합니다. 같은 사용자 내 name이 중복되면 409 RETROSPECTIVE_TEMPLATE_DUPLICATE를 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: Long,
        request: CreateRetrospectiveTemplateRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveTemplateResponse>>

    @Operation(
        summary = "회고 템플릿 수정",
        description = "템플릿의 name/questions를 수정합니다. " +
            "이름 변경으로 동일 사용자 내 중복이 발생하면 409 RETROSPECTIVE_TEMPLATE_DUPLICATE를 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 템플릿 ID", example = "3") id: Long,
        request: UpdateRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveTemplateResponse>

    @Operation(
        summary = "회고 템플릿 삭제",
        description = "템플릿을 hard delete 합니다. " +
            "이미 적용된 회고는 스냅샷이므로 영향이 없습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 템플릿 ID", example = "3") id: Long,
    ): ResponseEntity<Void>
}

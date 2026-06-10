package com.cheerup.demo.mail.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.mail.dto.ClassifiedMailMessagesResponse
import com.cheerup.demo.mail.domain.MailSuggestionStatus
import com.cheerup.demo.mail.dto.AnalyzeMailSuggestionRequest
import com.cheerup.demo.mail.dto.MailSuggestionResponse
import com.cheerup.demo.mail.dto.MailSuggestionsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Mail", description = "메일 연동 및 채용 메일 분류 API")
interface MailApi {

    @Operation(
        summary = "분류된 메일 목록 조회",
        description = """
            외부 메일 후보 목록을 조회하고, 현재 사용자의 Stage 목록을 기준으로 채용 관련 분류 결과를 붙여 반환합니다.

            현재 분류기는 키워드 기반 stub입니다. 메일 본문 전체는 목록 응답에 포함하지 않으며,
            Application/ScheduleEvent/Suggestion을 생성하거나 수정하지 않습니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.MAIL_CLIENT_NOT_CONFIGURED),
            SwaggerErrorResponse(ErrorCode.MAIL_PROVIDER_API_FAILED),
            SwaggerErrorResponse(ErrorCode.MAIL_INTEGRATION_REAUTH_REQUIRED),
            SwaggerErrorResponse(ErrorCode.MAIL_ACCESS_TOKEN_MISSING),
            SwaggerErrorResponse(ErrorCode.MAIL_REFRESH_TOKEN_MISSING),
            SwaggerErrorResponse(ErrorCode.AI_GENERATION_FAILED),
        ],
    )
    fun listClassifiedMessages(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회 개수", example = "20") limit: Int,
    ): ApiResponse<ClassifiedMailMessagesResponse>

    @Operation(
        summary = "메일 AI 분석 및 칸반 이동 제안 생성",
        description = """
            사용자가 선택한 메일 한 건과 채용 카드 한 건을 AI 서버로 분석합니다.
            메일 전체 본문은 외부 AI 호출 동안만 메모리에 유지하고 DB나 응답에 저장하지 않습니다.
            단계 분류와 칸반 이동 추천 결과는 Suggestion으로 저장되며, 카드는 수락 API 호출 전까지 변경되지 않습니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.MAIL_INTEGRATION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.STAGE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.MAIL_PROVIDER_API_FAILED),
            SwaggerErrorResponse(ErrorCode.AI_GENERATION_FAILED),
            SwaggerErrorResponse(ErrorCode.AI_GENERATION_TIMEOUT),
        ],
    )
    fun analyzeSuggestion(
        @Parameter(hidden = true) userId: Long,
        request: AnalyzeMailSuggestionRequest,
    ): ApiResponse<MailSuggestionResponse>

    @Operation(summary = "메일 AI 제안 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    fun listSuggestions(
        @Parameter(hidden = true) userId: Long,
        status: MailSuggestionStatus,
    ): ApiResponse<MailSuggestionsResponse>

    @Operation(summary = "메일 AI 칸반 이동 제안 수락")
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.SUGGESTION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.SUGGESTION_NOT_ACTIONABLE),
            SwaggerErrorResponse(ErrorCode.SUGGESTION_ALREADY_PROCESSED),
            SwaggerErrorResponse(ErrorCode.SUGGESTION_STALE),
        ],
    )
    fun acceptSuggestion(
        @Parameter(hidden = true) userId: Long,
        id: Long,
    ): ApiResponse<MailSuggestionResponse>

    @Operation(summary = "메일 AI 칸반 이동 제안 거절")
    @SecurityRequirement(name = "bearerAuth")
    fun rejectSuggestion(
        @Parameter(hidden = true) userId: Long,
        id: Long,
    ): ApiResponse<MailSuggestionResponse>
}

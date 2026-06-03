package com.cheerup.demo.mail.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.mail.dto.ClassifiedMailMessagesResponse
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
}

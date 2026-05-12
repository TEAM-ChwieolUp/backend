package com.cheerup.demo.mail.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.mail.dto.MailOAuthAuthorizeResponse
import com.cheerup.demo.mail.dto.MailIntegrationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Mail Integrations", description = "외부 메일 계정 OAuth 연결 API")
interface MailIntegrationApi {

    @Operation(
        summary = "메일 계정 OAuth 연결 시작",
        description = """
            로그인 사용자의 외부 메일 계정 권한 동의 URL을 생성합니다.

            Bearer JWT 기반 SPA에서는 브라우저 주소 이동에 Authorization 헤더를 실을 수 없으므로,
            프론트는 이 API를 호출해 받은 `authorizationUrl`로 `window.location.href`를 변경하면 됩니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.MAIL_OAUTH_PROVIDER_NOT_SUPPORTED),
            SwaggerErrorResponse(ErrorCode.MAIL_OAUTH_PROVIDER_NOT_CONFIGURED),
        ],
    )
    fun authorize(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "메일 제공자", example = "google") provider: String,
        @Parameter(description = "연동 완료 후 프론트에서 복귀할 경로") redirectAfter: String?,
    ): ApiResponse<MailOAuthAuthorizeResponse>

    @Operation(
        summary = "메일 계정 OAuth callback",
        description = "OAuth 제공자가 authorization code를 전달하는 백엔드 callback입니다. 프론트가 직접 호출하지 않습니다.",
    )
    fun callback(
        @Parameter(description = "메일 제공자", example = "google") provider: String,
        @Parameter(description = "Authorization code") code: String?,
        @Parameter(description = "CSRF 및 요청 식별 state") state: String?,
        @Parameter(description = "OAuth 제공자 오류 코드") error: String?,
    ): org.springframework.web.servlet.view.RedirectView

    @Operation(
        summary = "메일 계정 연결 목록 조회",
        description = "현재 로그인 사용자가 연결한 활성 메일 계정 목록을 조회합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        ],
    )
    fun list(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<List<MailIntegrationResponse>>

    @Operation(
        summary = "메일 계정 연결 해제",
        description = "현재 로그인 사용자가 연결한 메일 계정을 비활성화합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.MAIL_INTEGRATION_NOT_FOUND),
        ],
    )
    fun disconnect(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "메일 연동 ID", example = "1") integrationId: Long,
    ): ResponseEntity<Void>
}

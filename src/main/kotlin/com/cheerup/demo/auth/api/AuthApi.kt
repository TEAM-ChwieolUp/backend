package com.cheerup.demo.auth.api

import com.cheerup.demo.auth.dto.LoginResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Tag(name = "Auth", description = "OAuth2 로그인과 JWT 토큰 갱신 API")
interface AuthApi {

    @Operation(
        summary = "Access Token 재발급",
        description = "HttpOnly refresh token 쿠키를 검증하고 새 access token과 refresh token 쿠키를 발급합니다.",
    )
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.REFRESH_TOKEN_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.REFRESH_TOKEN_MISMATCH),
            SwaggerErrorResponse(ErrorCode.EXPIRED_TOKEN),
        ],
    )
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<LoginResponse>
}

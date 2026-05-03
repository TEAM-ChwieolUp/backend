package com.cheerup.demo.user.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.user.dto.MeResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User", description = "사용자 프로필 API")
interface UserApi {

    @Operation(
        summary = "내 프로필 조회",
        description = "JWT access token의 사용자 식별자를 기준으로 현재 로그인한 사용자의 프로필을 조회합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.USER_NOT_FOUND),
        ],
    )
    fun me(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<MeResponse>
}

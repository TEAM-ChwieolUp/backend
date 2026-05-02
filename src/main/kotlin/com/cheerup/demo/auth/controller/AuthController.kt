package com.cheerup.demo.auth.controller

import com.cheerup.demo.auth.dto.LoginResponse
import com.cheerup.demo.auth.service.AuthService
import com.cheerup.demo.auth.support.RefreshTokenCookieManager
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshTokenCookieManager: RefreshTokenCookieManager,
) {

    @PostMapping("/refresh")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<LoginResponse> {
        val refreshToken = refreshTokenCookieManager.extractToken(request)
            ?: throw BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
        val loginResult = authService.reissueByRefreshToken(refreshToken)

        refreshTokenCookieManager.addCookie(
            response = response,
            token = loginResult.refreshToken,
            maxAgeSeconds = loginResult.refreshTokenMaxAgeSeconds,
        )

        return ApiResponse.success(
            LoginResponse(
                accessToken = loginResult.accessToken,
                user = loginResult.user,
            ),
        )
    }
}

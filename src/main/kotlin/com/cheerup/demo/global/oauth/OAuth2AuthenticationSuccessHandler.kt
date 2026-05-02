package com.cheerup.demo.global.oauth

import com.cheerup.demo.auth.dto.LoginResponse
import com.cheerup.demo.auth.service.AuthService
import com.cheerup.demo.auth.support.RefreshTokenCookieManager
import com.cheerup.demo.global.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class OAuth2AuthenticationSuccessHandler(
    private val authService: AuthService,
    private val refreshTokenCookieManager: RefreshTokenCookieManager,
    private val objectMapper: ObjectMapper,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as? OAuth2AuthenticationPrincipal
            ?: throw IllegalStateException("OAuth2 principal is not supported.")
        val loginResult = authService.loginByOAuth2(principal.userInfo)

        refreshTokenCookieManager.addCookie(
            response = response,
            token = loginResult.refreshToken,
            maxAgeSeconds = loginResult.refreshTokenMaxAgeSeconds,
        )

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.writer,
            ApiResponse.success(
                LoginResponse(
                    accessToken = loginResult.accessToken,
                    user = loginResult.user,
                ),
            ),
        )
    }
}

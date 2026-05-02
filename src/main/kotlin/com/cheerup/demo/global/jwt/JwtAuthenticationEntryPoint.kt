package com.cheerup.demo.global.jwt

import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = ErrorCode.UNAUTHORIZED.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()

        objectMapper.writeValue(
            response.writer,
            ErrorResponse(
                code = ErrorCode.UNAUTHORIZED.code,
                message = ErrorCode.UNAUTHORIZED.message,
                detail = authException.message,
                path = request.requestURI,
            ),
        )
    }
}

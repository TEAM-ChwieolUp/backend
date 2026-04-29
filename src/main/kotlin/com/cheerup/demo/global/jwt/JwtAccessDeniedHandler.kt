package com.cheerup.demo.global.jwt

import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class JwtAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = ErrorCode.FORBIDDEN.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()

        objectMapper.writeValue(
            response.writer,
            ErrorResponse(
                code = ErrorCode.FORBIDDEN.code,
                message = ErrorCode.FORBIDDEN.message,
                detail = accessDeniedException.message,
                path = request.requestURI,
            ),
        )
    }
}

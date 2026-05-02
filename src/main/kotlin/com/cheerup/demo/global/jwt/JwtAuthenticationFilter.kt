package com.cheerup.demo.global.jwt

import com.cheerup.demo.global.exception.BusinessException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        val token = extractBearerToken(authorizationHeader)

        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            jwtProvider.validateAccessToken(token)
            val userId = jwtProvider.parseUserId(token)
            val principal = JwtPrincipal(userId)
            val authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )

            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
        } catch (ex: BusinessException) {
            SecurityContextHolder.clearContext()
            authenticationEntryPoint.commence(
                request,
                response,
                BadCredentialsException(ex.errorCode.message, ex),
            )
        }
    }

    private fun extractBearerToken(headerValue: String?): String? {
        if (headerValue.isNullOrBlank() || !headerValue.startsWith(BEARER_PREFIX)) {
            return null
        }

        return headerValue.removePrefix(BEARER_PREFIX).trim().takeIf { it.isNotBlank() }
    }

    companion object {
        private const val BEARER_PREFIX: String = "Bearer "
    }
}

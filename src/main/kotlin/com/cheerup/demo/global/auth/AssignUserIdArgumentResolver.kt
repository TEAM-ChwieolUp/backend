package com.cheerup.demo.global.auth

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.jwt.JwtPrincipal
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * `@AssignUserId` 가 붙은 핸들러 메서드에서 이름이 [USER_ID_PARAMETER_NAME] 인 Long/Long? 파라미터를
 * 현재 인증된 사용자의 userId 로 채운다. PathVariable 등 다른 Long 파라미터와 공존할 수 있도록
 * 단순한 "Long 한 개"가 아닌 파라미터 이름 기반으로 식별한다.
 */
@Component
class AssignUserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        parameter.method?.getAnnotation(AssignUserId::class.java) ?: return false
        if (!parameter.isUserIdParameter()) {
            return false
        }
        if (parameter.parameterName != USER_ID_PARAMETER_NAME) {
            return false
        }
        return true
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val annotation = requireNotNull(parameter.method?.getAnnotation(AssignUserId::class.java))
        validateNullability(parameter, annotation)

        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication == null || authentication is AnonymousAuthenticationToken) {
            if (annotation.required) {
                throw BusinessException(ErrorCode.UNAUTHORIZED)
            }
            return null
        }

        val principal = authentication.principal as? JwtPrincipal
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        return principal.userId
    }

    private fun validateNullability(parameter: MethodParameter, annotation: AssignUserId) {
        if (!annotation.required && parameter.parameterType == java.lang.Long.TYPE) {
            throw IllegalStateException("@AssignUserId(required = false) requires a nullable Long parameter.")
        }
    }

    private fun MethodParameter.isUserIdParameter(): Boolean =
        parameterType == java.lang.Long::class.java || parameterType == java.lang.Long.TYPE

    companion object {
        private const val USER_ID_PARAMETER_NAME = "userId"
    }
}

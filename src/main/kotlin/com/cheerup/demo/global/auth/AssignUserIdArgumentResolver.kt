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

@Component
class AssignUserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        val annotation = parameter.method?.getAnnotation(AssignUserId::class.java) ?: return false
        if (!parameter.isUserIdParameter()) {
            return false
        }

        validateMethodSignature(parameter, annotation)
        return true
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val annotation = requireNotNull(parameter.method?.getAnnotation(AssignUserId::class.java))
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

    private fun validateMethodSignature(
        parameter: MethodParameter,
        annotation: AssignUserId,
    ) {
        val method = requireNotNull(parameter.method)
        val userIdParameters = method.parameters.filter { methodParameter ->
            methodParameter.type == java.lang.Long::class.java || methodParameter.type == java.lang.Long.TYPE
        }

        require(userIdParameters.size == 1) {
            "@AssignUserId methods must declare exactly one Long or Long? parameter."
        }

        if (!annotation.required && parameter.parameterType == java.lang.Long.TYPE) {
            throw IllegalStateException("@AssignUserId(required = false) requires a nullable Long parameter.")
        }
    }

    private fun MethodParameter.isUserIdParameter(): Boolean =
        parameterType == java.lang.Long::class.java || parameterType == java.lang.Long.TYPE
}

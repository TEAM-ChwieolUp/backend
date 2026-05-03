package com.cheerup.demo.global.config.swagger

import com.cheerup.demo.global.exception.ErrorCode

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SwaggerErrorResponse(
    val value: ErrorCode,
    val description: String = "",
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SwaggerErrorResponses(
    val errors: Array<SwaggerErrorResponse>,
)

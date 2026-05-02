package com.cheerup.demo.global.auth

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AssignUserId(
    val required: Boolean = true,
)

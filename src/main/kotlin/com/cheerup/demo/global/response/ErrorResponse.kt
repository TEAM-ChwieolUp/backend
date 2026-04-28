package com.cheerup.demo.global.response

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: String? = null,
    val timestamp: Instant = Instant.now(),
    val path: String? = null,
)

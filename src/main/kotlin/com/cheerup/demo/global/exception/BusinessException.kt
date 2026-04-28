package com.cheerup.demo.global.exception

class BusinessException(
    val errorCode: ErrorCode,
    val detail: String? = null,
    cause: Throwable? = null,
) : RuntimeException(detail ?: errorCode.message, cause)

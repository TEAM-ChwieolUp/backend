package com.cheerup.demo.ai.client

class AiServerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AiServerTimeoutException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

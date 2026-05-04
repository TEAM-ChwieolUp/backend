package com.cheerup.demo.retrospective.ai

interface RetrospectiveQuestionGenerator {
    fun generate(context: RetrospectiveQuestionContext): List<String>
}

class RetrospectiveQuestionGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class RetrospectiveQuestionTimeoutException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

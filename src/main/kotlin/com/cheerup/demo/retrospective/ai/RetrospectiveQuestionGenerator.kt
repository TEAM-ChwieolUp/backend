package com.cheerup.demo.retrospective.ai

interface RetrospectiveQuestionGenerator {
    fun generate(context: RetrospectiveQuestionContext): RetrospectiveQuestionGenerationResult
}

data class RetrospectiveQuestionGenerationResult(
    val questionSetTitle: String,
    val jobRole: String,
    val processStage: String,
    val questions: List<GeneratedRetrospectiveQuestion>,
)

data class GeneratedRetrospectiveQuestion(
    val category: String,
    val question: String,
    val reason: String,
    val priority: String,
    val sourceTemplateIds: List<String>,
)

class RetrospectiveQuestionGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class RetrospectiveQuestionTimeoutException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

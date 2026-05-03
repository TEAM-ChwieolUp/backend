package com.cheerup.demo.retrospective.dto

import jakarta.validation.constraints.Positive

data class RetrospectiveQuestionRequest(
    @field:Positive
    val applicationId: Long,

    @field:Positive
    val stageId: Long? = null,
)

data class RetrospectiveQuestionsResponse(
    val questions: List<String>,
)

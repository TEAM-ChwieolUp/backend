package com.cheerup.demo.retrospective.dto

import jakarta.validation.constraints.Positive

data class RetrospectiveQuestionRequest(
    @field:Positive
    val applicationId: Long,

    @field:Positive
    val stageId: Long? = null,

    @field:Positive
    val questionCount: Int? = null,
)

data class RetrospectiveQuestionsResponse(
    val questionSetTitle: String,
    val jobRole: String,
    val processStage: String,
    val questions: List<RetrospectiveQuestionResponse>,
)

data class RetrospectiveQuestionResponse(
    val category: String,
    val question: String,
    val reason: String,
    val priority: String,
    val sourceTemplateIds: List<String>,
)

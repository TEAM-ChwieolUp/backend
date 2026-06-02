package com.cheerup.demo.retrospective.ai

import com.fasterxml.jackson.annotation.JsonProperty

data class AiRetrospectiveQuestionRequest(
    @JsonProperty("user_id")
    val userId: Long,

    @JsonProperty("job_posting_title")
    val jobPostingTitle: String,

    @JsonProperty("company_name")
    val companyName: String,

    @JsonProperty("job_role")
    val jobRole: String,

    @JsonProperty("process_stage")
    val processStage: String,

    @JsonProperty("question_count")
    val questionCount: Int,
)

data class AiRetrospectiveQuestionsResponse(
    @JsonProperty("question_set_title")
    val questionSetTitle: String? = null,

    @JsonProperty("job_role")
    val jobRole: String? = null,

    @JsonProperty("process_stage")
    val processStage: String? = null,

    val questions: List<AiRetrospectiveQuestion>? = null,
)

data class AiRetrospectiveQuestion(
    val category: String? = null,
    val question: String? = null,
    val reason: String? = null,
    val priority: String? = null,

    @JsonProperty("source_template_ids")
    val sourceTemplateIds: List<String>? = null,
)

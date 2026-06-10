package com.cheerup.demo.mail.ai

import com.fasterxml.jackson.annotation.JsonProperty

data class AiStage(
    val id: Long,
    val name: String,
    val description: String? = null,
    val order: Int? = null,
)

data class MailStageClassifyRequest(
    @JsonProperty("mail_subject")
    val mailSubject: String,
    @JsonProperty("mail_body")
    val mailBody: String,
    @JsonProperty("user_stage_categories")
    val userStageCategories: List<AiStage>,
)

data class MailStageClassifyResponse(
    @JsonProperty("predicted_stage")
    val predictedStage: AiStage? = null,
    val confidence: Double? = null,
    val reason: String? = null,
    val evidence: List<String>? = null,
    @JsonProperty("needs_user_confirmation")
    val needsUserConfirmation: Boolean? = null,
)

data class KanbanMoveRecommendRequest(
    @JsonProperty("mail_subject")
    val mailSubject: String,
    @JsonProperty("mail_body")
    val mailBody: String,
    @JsonProperty("current_kanban_stage")
    val currentKanbanStage: AiStage,
    @JsonProperty("user_kanban_stages")
    val userKanbanStages: List<AiStage>,
)

data class KanbanMoveRecommendResponse(
    @JsonProperty("recommend_move")
    val recommendMove: Boolean? = null,
    @JsonProperty("from_stage")
    val fromStage: AiStage? = null,
    @JsonProperty("to_stage")
    val toStage: AiStage? = null,
    val confidence: Double? = null,
    val reason: String? = null,
    val evidence: List<String>? = null,
    @JsonProperty("needs_user_confirmation")
    val needsUserConfirmation: Boolean? = null,
)

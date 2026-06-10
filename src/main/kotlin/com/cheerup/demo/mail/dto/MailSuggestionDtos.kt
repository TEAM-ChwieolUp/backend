package com.cheerup.demo.mail.dto

import com.cheerup.demo.mail.domain.MailSuggestion
import com.cheerup.demo.mail.domain.MailSuggestionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.Instant

data class AnalyzeMailSuggestionRequest(
    @field:Positive
    val integrationId: Long,
    @field:NotBlank
    val messageId: String,
    @field:Positive
    val applicationId: Long,
)

data class MailSuggestionsResponse(
    val suggestions: List<MailSuggestionResponse>,
)

data class MailSuggestionResponse(
    val id: Long,
    val integrationId: Long,
    val messageId: String,
    val applicationId: Long,
    val predictedStage: SuggestedStageResponse?,
    val classificationConfidence: Double,
    val classificationReason: String,
    val classificationEvidence: List<String>,
    val recommendMove: Boolean,
    val fromStage: SuggestedStageResponse,
    val toStage: SuggestedStageResponse?,
    val moveConfidence: Double,
    val moveReason: String,
    val moveEvidence: List<String>,
    val needsUserConfirmation: Boolean,
    val status: MailSuggestionStatus,
    val processedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SuggestedStageResponse(
    val id: Long,
    val name: String,
)

fun MailSuggestion.toResponse(): MailSuggestionResponse =
    MailSuggestionResponse(
        id = requireNotNull(id) { "MailSuggestion must be persisted" },
        integrationId = integrationId,
        messageId = messageId,
        applicationId = applicationId,
        predictedStage = predictedStageId?.let {
            SuggestedStageResponse(it, requireNotNull(predictedStageName))
        },
        classificationConfidence = classificationConfidence,
        classificationReason = classificationReason,
        classificationEvidence = classificationEvidence.toList(),
        recommendMove = toStageId != null,
        fromStage = SuggestedStageResponse(fromStageId, fromStageName),
        toStage = toStageId?.let { SuggestedStageResponse(it, requireNotNull(toStageName)) },
        moveConfidence = moveConfidence,
        moveReason = moveReason,
        moveEvidence = moveEvidence.toList(),
        needsUserConfirmation = true,
        status = status,
        processedAt = processedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

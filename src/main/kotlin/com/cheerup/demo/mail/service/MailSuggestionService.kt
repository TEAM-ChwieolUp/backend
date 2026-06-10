package com.cheerup.demo.mail.service

import com.cheerup.demo.ai.client.AiServerException
import com.cheerup.demo.ai.client.AiServerTimeoutException
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.application.service.ApplicationService
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.ai.AiStage
import com.cheerup.demo.mail.ai.KanbanMoveRecommendRequest
import com.cheerup.demo.mail.ai.MailAiAnalysisCoordinator
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationCommand
import com.cheerup.demo.mail.classifier.StageCandidate
import com.cheerup.demo.mail.client.MailClientRegistry
import com.cheerup.demo.mail.client.MailIntegrationContext
import com.cheerup.demo.mail.client.MailMessageCandidate
import com.cheerup.demo.mail.client.toContext
import com.cheerup.demo.mail.domain.MailSuggestion
import com.cheerup.demo.mail.domain.MailSuggestionStatus
import com.cheerup.demo.mail.dto.AnalyzeMailSuggestionRequest
import com.cheerup.demo.mail.dto.MailSuggestionResponse
import com.cheerup.demo.mail.dto.MailSuggestionsResponse
import com.cheerup.demo.mail.dto.toResponse
import com.cheerup.demo.mail.oauth.MailTokenCipher
import com.cheerup.demo.mail.repository.MailIntegrationRepository
import com.cheerup.demo.mail.repository.MailSuggestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional(readOnly = true)
class MailSuggestionService(
    private val integrationRepository: MailIntegrationRepository,
    private val suggestionRepository: MailSuggestionRepository,
    private val applicationRepository: ApplicationRepository,
    private val stageRepository: StageRepository,
    private val mailClientRegistry: MailClientRegistry,
    private val mailTokenCipher: MailTokenCipher,
    private val aiAnalysisCoordinator: MailAiAnalysisCoordinator,
    private val applicationService: ApplicationService,
    private val clock: Clock,
) {

    @Transactional
    fun analyze(userId: Long, request: AnalyzeMailSuggestionRequest): MailSuggestionResponse {
        val integration = loadIntegration(userId, request.integrationId)
        val application = applicationRepository.findByIdAndUserId(request.applicationId, userId)
            ?: throw BusinessException(
                ErrorCode.APPLICATION_NOT_FOUND,
                detail = "applicationId=${request.applicationId}",
            )
        val stages = stageRepository.findAllByUserIdOrderByDisplayOrderAsc(userId)
        val currentStage = stages.firstOrNull { it.id == application.stageId }
            ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=${application.stageId}")
        val stageCandidates = stages.map {
            StageCandidate(
                id = requireNotNull(it.id),
                name = it.name,
                category = it.category,
                order = it.displayOrder,
            )
        }
        val aiStages = stageCandidates.map { AiStage(it.id, it.name, order = it.order) }

        val content = mailClientRegistry.get(integration.provider)
            .getMessageContent(integration, request.messageId)
        val transientMessage = MailMessageCandidate(
            integrationId = integration.integrationId,
            provider = integration.provider,
            accountEmail = integration.email,
            messageId = request.messageId,
            threadId = "",
            subject = content.subject,
            from = "",
            receivedAt = Instant.EPOCH,
            snippet = "",
        )

        val analysis = try {
            aiAnalysisCoordinator.analyze(
                classificationCommand = RecruitmentMailClassificationCommand(
                    message = transientMessage,
                    stages = stageCandidates,
                    mailBody = content.body,
                ),
                moveRequest = KanbanMoveRecommendRequest(
                    mailSubject = content.subject,
                    mailBody = content.body,
                    currentKanbanStage = AiStage(
                        id = requireNotNull(currentStage.id),
                        name = currentStage.name,
                        order = currentStage.displayOrder,
                    ),
                    userKanbanStages = aiStages,
                ),
            )
        } catch (ex: AiServerTimeoutException) {
            throw BusinessException(ErrorCode.AI_GENERATION_TIMEOUT, cause = ex)
        } catch (ex: AiServerException) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = ex)
        }
        val classification = analysis.classification
        val move = analysis.move

        val toStage = move.toStage
        if (move.fromStage?.id != currentStage.id) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, detail = "AI returned an invalid source stage.")
        }
        if (toStage != null && stages.none { it.id == toStage.id }) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, detail = "AI returned an unknown target stage.")
        }
        if (move.recommendMove == true && toStage?.id == currentStage.id) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, detail = "AI recommended the current stage.")
        }

        val pending = suggestionRepository
            .findFirstByUserIdAndIntegrationIdAndMessageIdAndApplicationIdAndStatusOrderByIdDesc(
                userId = userId,
                integrationId = request.integrationId,
                messageId = request.messageId,
                applicationId = request.applicationId,
                status = MailSuggestionStatus.PENDING,
            )
        val suggestion = pending ?: MailSuggestion(
            userId = userId,
            integrationId = request.integrationId,
            messageId = request.messageId,
            applicationId = request.applicationId,
            classificationConfidence = classification.confidence,
            classificationReason = classification.reason,
            fromStageId = requireNotNull(move.fromStage).id,
            fromStageName = move.fromStage.name,
            moveConfidence = requireNotNull(move.confidence),
            moveReason = requireNotNull(move.reason),
            status = MailSuggestionStatus.PENDING,
        )

        suggestion.predictedStageId = classification.recommendedStageId
        suggestion.predictedStageName = classification.recommendedStageName
        suggestion.classificationConfidence = classification.confidence
        suggestion.classificationReason = classification.reason
        suggestion.classificationEvidence = classification.evidence.toMutableList()
        suggestion.fromStageId = requireNotNull(move.fromStage).id
        suggestion.fromStageName = move.fromStage.name
        suggestion.toStageId = toStage?.id
        suggestion.toStageName = toStage?.name
        suggestion.moveConfidence = requireNotNull(move.confidence)
        suggestion.moveReason = requireNotNull(move.reason)
        suggestion.moveEvidence = move.evidence.orEmpty().take(3).toMutableList()
        suggestion.status = if (move.recommendMove == true) {
            MailSuggestionStatus.PENDING
        } else {
            MailSuggestionStatus.NO_ACTION
        }
        suggestion.processedAt = if (suggestion.status == MailSuggestionStatus.NO_ACTION) clock.instant() else null

        return suggestionRepository.saveAndFlush(suggestion).toResponse()
    }

    fun list(userId: Long, status: MailSuggestionStatus): MailSuggestionsResponse =
        MailSuggestionsResponse(
            suggestionRepository.findAllByUserIdAndStatusOrderByIdDesc(userId, status)
                .map { it.toResponse() },
        )

    @Transactional
    fun accept(userId: Long, suggestionId: Long): MailSuggestionResponse {
        val suggestion = getPending(userId, suggestionId)
        val toStageId = suggestion.toStageId
            ?: throw BusinessException(ErrorCode.SUGGESTION_NOT_ACTIONABLE)

        applicationService.applyAiStageSuggestion(
            userId = userId,
            applicationId = suggestion.applicationId,
            expectedFromStageId = suggestion.fromStageId,
            toStageId = toStageId,
        )
        suggestion.accept(clock.instant())
        return suggestionRepository.saveAndFlush(suggestion).toResponse()
    }

    @Transactional
    fun reject(userId: Long, suggestionId: Long): MailSuggestionResponse {
        val suggestion = getPending(userId, suggestionId)
        suggestion.reject(clock.instant())
        return suggestionRepository.saveAndFlush(suggestion).toResponse()
    }

    private fun getPending(userId: Long, suggestionId: Long): MailSuggestion {
        val suggestion = suggestionRepository.findByIdAndUserId(suggestionId, userId)
            ?: throw BusinessException(ErrorCode.SUGGESTION_NOT_FOUND, detail = "suggestionId=$suggestionId")
        if (suggestion.status != MailSuggestionStatus.PENDING) {
            throw BusinessException(
                ErrorCode.SUGGESTION_ALREADY_PROCESSED,
                detail = "status=${suggestion.status}",
            )
        }
        return suggestion
    }

    private fun loadIntegration(userId: Long, integrationId: Long): MailIntegrationContext {
        val integration = integrationRepository.findByIdAndUserIdAndActiveTrue(integrationId, userId)
            ?: throw BusinessException(ErrorCode.MAIL_INTEGRATION_NOT_FOUND, detail = "integrationId=$integrationId")
        return integration.toContext().copy(
            accessToken = mailTokenCipher.decrypt(integration.accessToken),
            refreshToken = mailTokenCipher.decrypt(integration.refreshToken),
        )
    }
}

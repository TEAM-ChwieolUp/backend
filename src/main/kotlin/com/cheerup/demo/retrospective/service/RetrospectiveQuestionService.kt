package com.cheerup.demo.retrospective.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.ai.RetrospectiveAiProperties
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionContext
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerationException
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerationResult
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerator
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionTimeoutException
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionsResponse
import org.springframework.stereotype.Service

@Service
class RetrospectiveQuestionService(
    private val applicationRepository: ApplicationRepository,
    private val stageRepository: StageRepository,
    private val questionGenerator: RetrospectiveQuestionGenerator,
    private val rateLimiter: RetrospectiveAiRateLimiter,
    private val properties: RetrospectiveAiProperties,
) {

    fun generateQuestions(
        userId: Long,
        request: RetrospectiveQuestionRequest,
    ): RetrospectiveQuestionsResponse {
        val application = applicationRepository.findByIdAndUserId(request.applicationId, userId)
            ?: throw BusinessException(
                ErrorCode.APPLICATION_NOT_FOUND,
                detail = "applicationId=${request.applicationId}",
            )

        val stage = if (request.stageId != null) {
            stageRepository.findByIdAndUserId(request.stageId, userId)
                ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=${request.stageId}")
        } else {
            stageRepository.findByIdAndUserId(application.stageId, userId)
        }

        val questionCount = request.questionCount ?: properties.defaultQuestionCount
        if (questionCount !in 1..properties.maxQuestionCount) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                detail = "questionCount must be between 1 and ${properties.maxQuestionCount}.",
            )
        }

        if (!rateLimiter.tryAcquire(userId)) {
            throw BusinessException(ErrorCode.RATE_LIMITED)
        }

        val generated = try {
            questionGenerator.generate(
                RetrospectiveQuestionContext(
                    userId = userId,
                    jobPostingTitle = "${application.companyName} ${application.position} 채용",
                    companyName = application.companyName,
                    jobRole = application.position,
                    processStage = stage?.name ?: DEFAULT_PROCESS_STAGE,
                    questionCount = questionCount,
                ),
            )
        } catch (ex: RetrospectiveQuestionTimeoutException) {
            throw BusinessException(ErrorCode.AI_GENERATION_TIMEOUT, cause = ex)
        } catch (ex: RetrospectiveQuestionGenerationException) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = ex)
        } catch (ex: RuntimeException) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = ex)
        }

        return generated.toResponse()
    }

    private fun RetrospectiveQuestionGenerationResult.toResponse(): RetrospectiveQuestionsResponse {
        val questions = questions
            .asSequence()
            .filter { it.question.isNotBlank() && it.question.length <= MAX_QUESTION_LENGTH }
            .distinctBy { it.question }
            .take(properties.maxQuestionCount)
            .map {
                RetrospectiveQuestionResponse(
                    category = it.category,
                    question = it.question,
                    reason = it.reason,
                    priority = it.priority,
                    sourceTemplateIds = it.sourceTemplateIds,
                )
            }
            .toList()

        if (questions.isEmpty()) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, detail = "No valid questions generated.")
        }

        return RetrospectiveQuestionsResponse(
            questionSetTitle = questionSetTitle,
            jobRole = jobRole,
            processStage = processStage,
            questions = questions,
        )
    }

    companion object {
        private const val DEFAULT_PROCESS_STAGE = "전체 전형"
        private const val MAX_QUESTION_LENGTH = 1000
    }
}

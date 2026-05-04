package com.cheerup.demo.retrospective.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionContext
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerationException
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerator
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionTimeoutException
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionsResponse
import org.springframework.stereotype.Service

@Service
class RetrospectiveQuestionService(
    private val applicationRepository: ApplicationRepository,
    private val stageRepository: StageRepository,
    private val questionGenerator: RetrospectiveQuestionGenerator,
    private val rateLimiter: RetrospectiveAiRateLimiter,
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

        val stage = request.stageId?.let { stageId ->
            stageRepository.findByIdAndUserId(stageId, userId)
                ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=$stageId")
        }

        if (!rateLimiter.tryAcquire(userId)) {
            throw BusinessException(ErrorCode.RATE_LIMITED)
        }

        val generated = try {
            questionGenerator.generate(
                RetrospectiveQuestionContext(
                    companyName = application.companyName,
                    position = application.position,
                    memo = application.memo,
                    stageName = stage?.name,
                    stageCategory = stage?.category,
                ),
            )
        } catch (ex: RetrospectiveQuestionTimeoutException) {
            throw BusinessException(ErrorCode.AI_GENERATION_TIMEOUT, cause = ex)
        } catch (ex: RetrospectiveQuestionGenerationException) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = ex)
        } catch (ex: RuntimeException) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, cause = ex)
        }

        val questions = generated
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= MAX_QUESTION_LENGTH }
            .distinct()
            .take(MAX_QUESTION_COUNT)

        if (questions.isEmpty()) {
            throw BusinessException(ErrorCode.AI_GENERATION_FAILED, detail = "No valid questions generated.")
        }

        return RetrospectiveQuestionsResponse(questions)
    }

    companion object {
        private const val MAX_QUESTION_COUNT = 15
        private const val MAX_QUESTION_LENGTH = 1000
    }
}

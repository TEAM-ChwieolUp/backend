package com.cheerup.demo.retrospective.ai

import com.cheerup.demo.ai.client.AiServerException
import com.cheerup.demo.ai.client.AiServerProperties
import com.cheerup.demo.ai.client.AiServerTimeoutException
import com.cheerup.demo.ai.client.callAiServer
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ExternalRetrospectiveQuestionGenerator(
    private val restClient: RestClient,
    private val properties: AiServerProperties,
) : RetrospectiveQuestionGenerator {

    override fun generate(context: RetrospectiveQuestionContext): RetrospectiveQuestionGenerationResult {
        val request = AiRetrospectiveQuestionRequest(
            userId = context.userId,
            jobPostingTitle = context.jobPostingTitle,
            companyName = context.companyName,
            jobRole = context.jobRole,
            processStage = context.processStage,
            questionCount = context.questionCount,
        )

        val response = try {
            callAiServer("AI retrospective question generation") {
            restClient.post()
                .uri(properties.retrospectiveQuestionsPath)
                .body(request)
                .retrieve()
                .requiredBody(AiRetrospectiveQuestionsResponse::class.java)
            }
        } catch (ex: AiServerTimeoutException) {
            throw RetrospectiveQuestionTimeoutException(ex.message ?: "AI request timed out.", ex)
        } catch (ex: AiServerException) {
            throw RetrospectiveQuestionGenerationException(ex.message ?: "AI request failed.", ex)
        }

        return response.toResult()
    }

    private fun AiRetrospectiveQuestionsResponse.toResult(): RetrospectiveQuestionGenerationResult {
        val result = RetrospectiveQuestionGenerationResult(
            questionSetTitle = questionSetTitle.requiredText("question_set_title"),
            jobRole = jobRole.requiredText("job_role"),
            processStage = processStage.requiredText("process_stage"),
            questions = questions
                .orEmpty()
                .mapNotNull { it.toGeneratedQuestionOrNull() },
        )

        if (result.questions.isEmpty()) {
            throw RetrospectiveQuestionGenerationException("AI retrospective question response has no valid questions.")
        }

        return result
    }

    private fun AiRetrospectiveQuestion.toGeneratedQuestionOrNull(): GeneratedRetrospectiveQuestion? {
        val questionText = question?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_QUESTION_LENGTH }
            ?: return null

        return GeneratedRetrospectiveQuestion(
            category = category.requiredText("questions[].category"),
            question = questionText,
            reason = reason.requiredText("questions[].reason"),
            priority = priority.requiredText("questions[].priority"),
            sourceTemplateIds = sourceTemplateIds
                ?: throw RetrospectiveQuestionGenerationException("Missing questions[].source_template_ids."),
        )
    }

    private fun String?.requiredText(fieldName: String): String =
        this?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw RetrospectiveQuestionGenerationException("Missing $fieldName.")

    companion object {
        private const val MAX_QUESTION_LENGTH = 1000
    }
}

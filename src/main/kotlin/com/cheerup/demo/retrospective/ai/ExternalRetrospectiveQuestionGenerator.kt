package com.cheerup.demo.retrospective.ai

import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException

@Component
class ExternalRetrospectiveQuestionGenerator(
    private val restClient: RestClient,
    private val properties: RetrospectiveAiProperties,
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
            restClient.post()
                .uri(properties.questionPath)
                .body(request)
                .retrieve()
                .requiredBody(AiRetrospectiveQuestionsResponse::class.java)
        } catch (ex: ResourceAccessException) {
            if (ex.containsTimeout()) {
                throw RetrospectiveQuestionTimeoutException("AI retrospective question generation timed out.", ex)
            }
            throw RetrospectiveQuestionGenerationException("AI retrospective question generation request failed.", ex)
        } catch (ex: RestClientResponseException) {
            throw RetrospectiveQuestionGenerationException(
                "AI retrospective question generation returned HTTP ${ex.statusCode.value()}.",
                ex,
            )
        } catch (ex: RestClientException) {
            throw RetrospectiveQuestionGenerationException("AI retrospective question generation response was invalid.", ex)
        } catch (ex: RuntimeException) {
            throw RetrospectiveQuestionGenerationException("AI retrospective question generation failed.", ex)
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

    private fun Throwable.containsTimeout(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SocketTimeoutException || current is HttpTimeoutException) {
                return true
            }
            val message = current.message?.lowercase()
            if (message?.contains("timeout") == true || message?.contains("timed out") == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        private const val MAX_QUESTION_LENGTH = 1000
    }
}

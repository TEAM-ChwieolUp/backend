package com.cheerup.demo.mail.classifier

import com.cheerup.demo.ai.client.AiServerProperties
import com.cheerup.demo.ai.client.callAiServer
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.mail.ai.AiStage
import com.cheerup.demo.mail.ai.MailStageClassifyRequest
import com.cheerup.demo.mail.ai.MailStageClassifyResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class AiRecruitmentMailClassifier(
    private val restClient: RestClient,
    private val properties: AiServerProperties,
) : RecruitmentMailClassifier {

    override fun classify(command: RecruitmentMailClassificationCommand): RecruitmentMailClassificationResult {
        val stagesById = command.stages.associateBy { it.id }
        return callAiServer("AI mail stage classification") {
            val response =
            restClient.post()
                .uri(properties.mailStageClassifyPath)
                .body(
                    MailStageClassifyRequest(
                        mailSubject = command.message.subject,
                        mailBody = command.mailBody,
                        userStageCategories = command.stages.map {
                            AiStage(id = it.id, name = it.name, order = it.order)
                        },
                    ),
                )
                .retrieve()
                .requiredBody(MailStageClassifyResponse::class.java)

            val confidence = requireNotNull(response.confidence) { "Missing confidence" }
            val reason = response.reason?.trim().orEmpty()
            require(reason.isNotBlank()) { "Missing reason" }
            require(response.needsUserConfirmation == true) { "User confirmation must be required" }
            val predicted = response.predictedStage
            val matchedStage = predicted?.let { stagesById[it.id] }
            require(predicted == null || matchedStage != null) { "AI returned an unknown stage" }

            RecruitmentMailClassificationResult(
                isRecruitmentMail = predicted != null,
                stageCategory = matchedStage?.category ?: StageCategory.IN_PROGRESS.takeIf { predicted != null },
                recommendedStageId = predicted?.id,
                recommendedStageName = predicted?.name,
                confidence = confidence,
                reason = reason,
                evidence = response.evidence.orEmpty().take(3),
                needsUserConfirmation = true,
            )
        }
    }
}

package com.cheerup.demo.mail.dto

import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationResult
import com.cheerup.demo.mail.client.MailMessageCandidate
import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant

data class ClassifiedMailMessagesResponse(
    val messages: List<ClassifiedMailMessageResponse>,
)

data class ClassifiedMailMessageResponse(
    val integrationId: Long?,
    val provider: MailProvider,
    val accountEmail: String,
    val messageId: String,
    val threadId: String,
    val subject: String,
    val from: String,
    val receivedAt: Instant,
    val snippet: String,
    val classification: MailClassificationResponse,
)

data class MailClassificationResponse(
    val isRecruitmentMail: Boolean,
    val stageCategory: StageCategory?,
    val recommendedStageId: Long?,
    val recommendedStageName: String?,
    val confidence: Double,
    val reason: String,
)

fun MailMessageCandidate.toResponse(
    classification: RecruitmentMailClassificationResult,
): ClassifiedMailMessageResponse =
    ClassifiedMailMessageResponse(
        integrationId = integrationId,
        provider = provider,
        accountEmail = accountEmail,
        messageId = messageId,
        threadId = threadId,
        subject = subject,
        from = from,
        receivedAt = receivedAt,
        snippet = snippet,
        classification = MailClassificationResponse(
            isRecruitmentMail = classification.isRecruitmentMail,
            stageCategory = classification.stageCategory,
            recommendedStageId = classification.recommendedStageId,
            recommendedStageName = classification.recommendedStageName,
            confidence = classification.confidence,
            reason = classification.reason,
        ),
    )

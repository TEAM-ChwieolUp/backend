package com.cheerup.demo.mail.classifier

import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.mail.client.MailMessageCandidate

interface RecruitmentMailClassifier {
    fun classify(command: RecruitmentMailClassificationCommand): RecruitmentMailClassificationResult
}

data class RecruitmentMailClassificationCommand(
    val message: MailMessageCandidate,
    val stages: List<StageCandidate>,
    val mailBody: String = message.snippet,
)

data class StageCandidate(
    val id: Long,
    val name: String,
    val category: StageCategory,
    val order: Int? = null,
)

data class RecruitmentMailClassificationResult(
    val isRecruitmentMail: Boolean,
    val stageCategory: StageCategory?,
    val recommendedStageId: Long?,
    val recommendedStageName: String?,
    val confidence: Double,
    val reason: String,
    val evidence: List<String> = emptyList(),
    val needsUserConfirmation: Boolean = true,
)

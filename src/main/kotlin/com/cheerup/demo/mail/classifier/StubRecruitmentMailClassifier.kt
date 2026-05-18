package com.cheerup.demo.mail.classifier

import com.cheerup.demo.application.domain.StageCategory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "app.mail.classifier",
    name = ["mode"],
    havingValue = "stub",
    matchIfMissing = true,
)
class StubRecruitmentMailClassifier : RecruitmentMailClassifier {

    override fun classify(command: RecruitmentMailClassificationCommand): RecruitmentMailClassificationResult {
        val text = "${command.message.subject} ${command.message.snippet}"

        return when {
            text.containsAny(REJECTED_KEYWORDS) ->
                resultForFixedStage(
                    stages = command.stages,
                    category = StageCategory.REJECTED,
                    reason = "불합격 또는 전형 종료 키워드가 포함되어 있습니다.",
                )

            text.containsAny(PASSED_KEYWORDS) ->
                resultForFixedStage(
                    stages = command.stages,
                    category = StageCategory.PASSED,
                    reason = "합격 또는 오퍼 키워드가 포함되어 있습니다.",
                )

            text.containsAny(DOCUMENT_KEYWORDS) ->
                resultForProgressStage(command.stages, DOCUMENT_KEYWORDS, "서류 전형 키워드가 포함되어 있습니다.")

            text.containsAny(CODING_TEST_KEYWORDS) ->
                resultForProgressStage(command.stages, CODING_TEST_KEYWORDS, "코딩테스트 키워드가 포함되어 있습니다.")

            text.containsAny(INTERVIEW_KEYWORDS) ->
                resultForProgressStage(command.stages, INTERVIEW_KEYWORDS, "면접 키워드가 포함되어 있습니다.")

            text.containsAny(ASSIGNMENT_KEYWORDS) ->
                resultForProgressStage(command.stages, ASSIGNMENT_KEYWORDS, "과제 전형 키워드가 포함되어 있습니다.")

            text.containsAny(RECRUITMENT_KEYWORDS) ->
                RecruitmentMailClassificationResult(
                    isRecruitmentMail = true,
                    stageCategory = StageCategory.IN_PROGRESS,
                    recommendedStageId = null,
                    recommendedStageName = null,
                    confidence = 0.5,
                    reason = "채용 관련 키워드는 있으나 매칭 가능한 Stage를 찾지 못했습니다.",
                )

            else ->
                RecruitmentMailClassificationResult(
                    isRecruitmentMail = false,
                    stageCategory = null,
                    recommendedStageId = null,
                    recommendedStageName = null,
                    confidence = 0.0,
                    reason = "채용 관련 키워드를 찾지 못했습니다.",
                )
        }
    }

    private fun resultForFixedStage(
        stages: List<StageCandidate>,
        category: StageCategory,
        reason: String,
    ): RecruitmentMailClassificationResult {
        val stage = stages.firstOrNull { it.category == category }
        return RecruitmentMailClassificationResult(
            isRecruitmentMail = true,
            stageCategory = category,
            recommendedStageId = stage?.id,
            recommendedStageName = stage?.name,
            confidence = 0.8,
            reason = reason,
        )
    }

    private fun resultForProgressStage(
        stages: List<StageCandidate>,
        keywords: List<String>,
        reason: String,
    ): RecruitmentMailClassificationResult {
        val stage = stages
            .filter { it.category == StageCategory.IN_PROGRESS }
            .firstOrNull { stage -> stage.name.containsAny(keywords) }

        return RecruitmentMailClassificationResult(
            isRecruitmentMail = true,
            stageCategory = StageCategory.IN_PROGRESS,
            recommendedStageId = stage?.id,
            recommendedStageName = stage?.name,
            confidence = if (stage == null) 0.55 else 0.7,
            reason = reason,
        )
    }

    private fun String.containsAny(keywords: List<String>): Boolean =
        keywords.any { contains(it, ignoreCase = true) }

    companion object {
        private val PASSED_KEYWORDS = listOf("최종 합격", "합격", "오퍼", "offer", "passed")
        private val REJECTED_KEYWORDS = listOf("불합격", "아쉽게", "탈락", "rejected", "unfortunately")
        private val DOCUMENT_KEYWORDS = listOf("서류", "이력서", "자소서", "resume", "document")
        private val CODING_TEST_KEYWORDS = listOf("코딩테스트", "코딩 테스트", "코테", "coding", "test")
        private val INTERVIEW_KEYWORDS = listOf("면접", "interview")
        private val ASSIGNMENT_KEYWORDS = listOf("과제", "assignment", "homework")
        private val RECRUITMENT_KEYWORDS =
            PASSED_KEYWORDS + REJECTED_KEYWORDS + DOCUMENT_KEYWORDS +
                CODING_TEST_KEYWORDS + INTERVIEW_KEYWORDS + ASSIGNMENT_KEYWORDS +
                listOf("채용", "전형", "recruit", "career")
    }
}

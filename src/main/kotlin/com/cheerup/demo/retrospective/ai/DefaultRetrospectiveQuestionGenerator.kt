package com.cheerup.demo.retrospective.ai

import com.cheerup.demo.application.domain.StageCategory
import org.springframework.stereotype.Component

@Component
class DefaultRetrospectiveQuestionGenerator : RetrospectiveQuestionGenerator {

    override fun generate(context: RetrospectiveQuestionContext): List<String> {
        val company = context.companyName.ifBlank { "지원한 회사" }
        val position = context.position.ifBlank { "지원한 포지션" }
        val stage = context.stageName?.takeIf { it.isNotBlank() } ?: "전체 전형"

        val questions = mutableListOf(
            "$company $position 지원 과정에서 잘 풀린 부분은 무엇이었나요?",
            "$company $position 전형에서 가장 어려웠던 점은 무엇이었나요?",
            "$stage 단계에서 예상과 가장 달랐던 점은 무엇이었나요?",
            "다음에 비슷한 곳에 지원한다면 무엇을 바꾸시겠어요?",
            "준비 과정에서 부족했다고 느낀 정보나 자료는 무엇이었나요?",
            "다음번에 가장 먼저 점검해야 할 체크리스트 항목은 무엇인가요?",
        )

        questions += stageSpecificQuestions(stage)
        questions += categorySpecificQuestions(context.stageCategory)
        context.memo?.takeIf { it.isNotBlank() }?.let {
            questions += "메모 중 다시 살펴봐야 할 내용은 무엇인가요?"
        }

        return questions.distinct().take(15)
    }

    private fun stageSpecificQuestions(stage: String): List<String> =
        when {
            stage.containsAnyIgnoreCase("면접", "interview") -> listOf(
                "면접에서 답변하기 가장 어려웠던 질문은 무엇이었나요?",
                "어떤 답변에 더 구체적인 근거나 구조가 필요했다고 느끼셨나요?",
                "회사나 직무에 대해 추가로 조사했어야 할 내용은 무엇인가요?",
            )

            stage.containsAnyIgnoreCase("코딩", "코테", "테스트", "coding", "test") -> listOf(
                "코딩 테스트에서 시간이 가장 많이 소요된 부분은 어디였나요?",
                "디버깅이나 시간 분배에서 개선하고 싶은 결정은 무엇인가요?",
                "다음에 다시 짚어봐야 할 알고리즘이나 구현 패턴은 무엇인가요?",
            )

            stage.containsAnyIgnoreCase("서류", "자소서", "이력서", "resume", "document") -> listOf(
                "이 직무에 비해 가장 약하다고 느낀 자기소개서/이력서 문장은 무엇인가요?",
                "어떤 경험을 더 구체적인 임팩트로 풀어 썼어야 했나요?",
                "다음 지원 전에 가장 먼저 보완해야 할 부분은 무엇인가요?",
            )

            else -> listOf(
                "$stage 단계를 통해 회사나 직무에 대해 새롭게 알게 된 점은 무엇인가요?",
                "다음 $stage 단계 전에 준비 방식을 어떻게 바꾸시겠어요?",
            )
        }

    private fun categorySpecificQuestions(category: StageCategory?): List<String> =
        when (category) {
            StageCategory.PASSED -> listOf(
                "이 단계를 통과하는 데 가장 크게 작용한 강점은 무엇이라고 보시나요?",
                "그 강점을 다음 단계에서 어떻게 다시 활용할 수 있을까요?",
            )

            StageCategory.REJECTED -> listOf(
                "탈락 이후 가장 먼저 보완해야 할 것 같은 부족한 점은 무엇인가요?",
                "다음에 같은 실수를 줄이기 위해 할 수 있는 구체적인 행동은 무엇인가요?",
            )

            StageCategory.IN_PROGRESS,
            null,
            -> emptyList()
        }

    private fun String.containsAnyIgnoreCase(vararg keywords: String): Boolean =
        keywords.any { contains(it, ignoreCase = true) }
}

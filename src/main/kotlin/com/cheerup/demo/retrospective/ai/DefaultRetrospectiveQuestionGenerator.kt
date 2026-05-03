package com.cheerup.demo.retrospective.ai

import com.cheerup.demo.application.domain.StageCategory
import org.springframework.stereotype.Component

@Component
class DefaultRetrospectiveQuestionGenerator : RetrospectiveQuestionGenerator {

    override fun generate(context: RetrospectiveQuestionContext): List<String> {
        val company = context.companyName.ifBlank { "target company" }
        val position = context.position.ifBlank { "target position" }
        val stage = context.stageName?.takeIf { it.isNotBlank() } ?: "overall process"

        val questions = mutableListOf(
            "What worked well while applying to $company for $position?",
            "What was the hardest part of the $company $position process?",
            "What expectation differed from reality during the $stage stage?",
            "What should be changed before the next similar application?",
            "Which information or material was missing during preparation?",
            "What checklist item should be reviewed first next time?",
        )

        questions += stageSpecificQuestions(stage)
        questions += categorySpecificQuestions(context.stageCategory)
        context.memo?.takeIf { it.isNotBlank() }?.let {
            questions += "Which note from the memo should be reviewed again?"
        }

        return questions.distinct().take(15)
    }

    private fun stageSpecificQuestions(stage: String): List<String> =
        when {
            stage.contains("interview", ignoreCase = true) -> listOf(
                "Which interview question was hardest to answer?",
                "Which answer needs stronger evidence or structure?",
                "What company or role detail should be researched further?",
            )

            stage.contains("coding", ignoreCase = true) || stage.contains("test", ignoreCase = true) -> listOf(
                "Where did the most time go during the coding test?",
                "Which debugging or time allocation choice should be improved?",
                "Which algorithm or implementation pattern should be reviewed next?",
            )

            stage.contains("resume", ignoreCase = true) || stage.contains("document", ignoreCase = true) -> listOf(
                "Which resume sentence felt weakest for the role?",
                "Which experience should be described with more concrete impact?",
                "What should be improved before submitting the next application?",
            )

            else -> listOf(
                "What did the $stage stage reveal about the company or role?",
                "How should preparation change before the next $stage stage?",
            )
        }

    private fun categorySpecificQuestions(category: StageCategory?): List<String> =
        when (category) {
            StageCategory.PASSED -> listOf(
                "Which strength most likely helped pass this stage?",
                "How should that strength be reused in the next stage?",
            )

            StageCategory.REJECTED -> listOf(
                "What is the first likely gap to address after the rejection?",
                "What concrete action would reduce the same mistake next time?",
            )

            StageCategory.IN_PROGRESS,
            null,
            -> emptyList()
        }
}

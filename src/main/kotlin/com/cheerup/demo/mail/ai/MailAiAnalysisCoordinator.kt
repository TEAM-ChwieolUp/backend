package com.cheerup.demo.mail.ai

import com.cheerup.demo.ai.client.AiServerException
import com.cheerup.demo.ai.client.AiServerTimeoutException
import com.cheerup.demo.mail.classifier.AiRecruitmentMailClassifier
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationCommand
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationResult
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@Component
class MailAiAnalysisCoordinator(
    private val mailStageClassifier: AiRecruitmentMailClassifier,
    private val moveRecommender: KanbanMoveRecommender,
    @Qualifier("aiTaskExecutor")
    private val executor: Executor,
) {

    fun analyze(
        classificationCommand: RecruitmentMailClassificationCommand,
        moveRequest: KanbanMoveRecommendRequest,
    ): MailAiAnalysisResult {
        val classificationFuture = CompletableFuture.supplyAsync(
            { runCatching { mailStageClassifier.classify(classificationCommand) } },
            executor,
        )
        val moveFuture = CompletableFuture.supplyAsync(
            { runCatching { moveRecommender.recommend(moveRequest) } },
            executor,
        )

        CompletableFuture.allOf(classificationFuture, moveFuture).join()

        val classificationResult = classificationFuture.join()
        val moveResult = moveFuture.join()
        throwIfFailed(
            classificationResult.exceptionOrNull(),
            moveResult.exceptionOrNull(),
        )

        return MailAiAnalysisResult(
            classification = classificationResult.getOrThrow(),
            move = moveResult.getOrThrow(),
        )
    }

    private fun throwIfFailed(vararg failures: Throwable?) {
        val causes = failures.filterNotNull()
        causes.filterIsInstance<AiServerTimeoutException>().firstOrNull()?.let { throw it }
        causes.filterIsInstance<AiServerException>().firstOrNull()?.let { throw it }
        causes.firstOrNull()?.let { throw it }
    }
}

data class MailAiAnalysisResult(
    val classification: RecruitmentMailClassificationResult,
    val move: KanbanMoveRecommendResponse,
)

package com.cheerup.demo.mail.ai

import com.cheerup.demo.ai.client.AiServerException
import com.cheerup.demo.ai.client.AiServerTimeoutException
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.mail.classifier.AiRecruitmentMailClassifier
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationCommand
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationResult
import com.cheerup.demo.mail.classifier.StageCandidate
import com.cheerup.demo.mail.client.MailMessageCandidate
import com.cheerup.demo.mail.domain.MailProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MailAiAnalysisCoordinatorTest {
    private val classifier = mockk<AiRecruitmentMailClassifier>()
    private val recommender = mockk<KanbanMoveRecommender>()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val coordinator = MailAiAnalysisCoordinator(classifier, recommender, executor)

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `starts classification and move recommendation concurrently`() {
        val bothStarted = CountDownLatch(2)
        val release = CountDownLatch(1)
        every { classifier.classify(any()) } answers {
            bothStarted.countDown()
            release.await(1, TimeUnit.SECONDS)
            classification()
        }
        every { recommender.recommend(any()) } answers {
            bothStarted.countDown()
            release.await(1, TimeUnit.SECONDS)
            move()
        }

        val caller = Executors.newSingleThreadExecutor()
        try {
            val resultFuture = caller.submit<MailAiAnalysisResult> {
                coordinator.analyze(classificationCommand(), moveRequest())
            }

            assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue()
            release.countDown()

            val result = resultFuture.get(1, TimeUnit.SECONDS)
            assertThat(result.classification.recommendedStageId).isEqualTo(3L)
            assertThat(result.move.toStage?.id).isEqualTo(3L)
        } finally {
            release.countDown()
            caller.shutdownNow()
        }
    }

    @Test
    fun `prioritizes timeout when either parallel call times out`() {
        every { classifier.classify(any()) } throws AiServerException("classification failed")
        every { recommender.recommend(any()) } throws AiServerTimeoutException("move timed out")

        assertThatThrownBy {
            coordinator.analyze(classificationCommand(), moveRequest())
        }.isInstanceOf(AiServerTimeoutException::class.java)
    }

    private fun classificationCommand() =
        RecruitmentMailClassificationCommand(
            message = MailMessageCandidate(
                integrationId = 1L,
                provider = MailProvider.GOOGLE,
                accountEmail = "user@example.com",
                messageId = "message-1",
                threadId = "thread-1",
                subject = "1차 면접 안내",
                from = "recruit@example.com",
                receivedAt = Instant.EPOCH,
                snippet = "",
            ),
            stages = stages(),
            mailBody = "1차 면접 일정을 안내드립니다.",
        )

    private fun moveRequest() =
        KanbanMoveRecommendRequest(
            mailSubject = "1차 면접 안내",
            mailBody = "1차 면접 일정을 안내드립니다.",
            currentKanbanStage = AiStage(2L, "서류", order = 1),
            userKanbanStages = listOf(
                AiStage(2L, "서류", order = 1),
                AiStage(3L, "1차 면접", order = 2),
            ),
        )

    private fun stages() =
        listOf(
            StageCandidate(2L, "서류", StageCategory.IN_PROGRESS, 1),
            StageCandidate(3L, "1차 면접", StageCategory.IN_PROGRESS, 2),
        )

    private fun classification() =
        RecruitmentMailClassificationResult(
            isRecruitmentMail = true,
            stageCategory = StageCategory.IN_PROGRESS,
            recommendedStageId = 3L,
            recommendedStageName = "1차 면접",
            confidence = 0.9,
            reason = "면접 안내",
        )

    private fun move() =
        KanbanMoveRecommendResponse(
            recommendMove = true,
            fromStage = AiStage(2L, "서류", order = 1),
            toStage = AiStage(3L, "1차 면접", order = 2),
            confidence = 0.88,
            reason = "면접 단계 이동",
            needsUserConfirmation = true,
        )
}

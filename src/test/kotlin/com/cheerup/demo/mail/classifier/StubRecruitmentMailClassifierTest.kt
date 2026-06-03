package com.cheerup.demo.mail.classifier

import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.mail.client.MailMessageCandidate
import com.cheerup.demo.mail.domain.MailProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class StubRecruitmentMailClassifierTest {

    private val classifier = StubRecruitmentMailClassifier()

    @Test
    fun `passed keyword maps to passed stage`() {
        val result = classify(subject = "[토스] 최종 합격 안내")

        assertTrue(result.isRecruitmentMail)
        assertEquals(StageCategory.PASSED, result.stageCategory)
        assertEquals(3L, result.recommendedStageId)
        assertEquals("최종 합격", result.recommendedStageName)
    }

    @Test
    fun `rejected keyword maps to rejected stage before passed substring`() {
        val result = classify(subject = "[쿠팡] 불합격 안내")

        assertTrue(result.isRecruitmentMail)
        assertEquals(StageCategory.REJECTED, result.stageCategory)
        assertEquals(4L, result.recommendedStageId)
        assertEquals("불합격", result.recommendedStageName)
    }

    @Test
    fun `coding test keyword maps to matching in progress stage`() {
        val result = classify(subject = "[카카오] 코딩테스트 안내")

        assertTrue(result.isRecruitmentMail)
        assertEquals(StageCategory.IN_PROGRESS, result.stageCategory)
        assertEquals(2L, result.recommendedStageId)
        assertEquals("코딩테스트", result.recommendedStageName)
    }

    @Test
    fun `progress keyword without matching stage keeps recommended stage empty`() {
        val result = classifier.classify(
            RecruitmentMailClassificationCommand(
                message = message(subject = "[라인] 과제 전형 안내"),
                stages = defaultStages().filterNot { it.name == "과제" },
            ),
        )

        assertTrue(result.isRecruitmentMail)
        assertEquals(StageCategory.IN_PROGRESS, result.stageCategory)
        assertNull(result.recommendedStageId)
        assertNull(result.recommendedStageName)
    }

    @Test
    fun `non recruitment mail is not classified`() {
        val result = classify(subject = "주간 뉴스레터", snippet = "이번 주 기술 아티클을 모았습니다.")

        assertFalse(result.isRecruitmentMail)
        assertNull(result.stageCategory)
        assertNull(result.recommendedStageId)
    }

    private fun classify(
        subject: String,
        snippet: String = "",
    ): RecruitmentMailClassificationResult =
        classifier.classify(
            RecruitmentMailClassificationCommand(
                message = message(subject = subject, snippet = snippet),
                stages = defaultStages(),
            ),
        )

    private fun message(
        subject: String,
        snippet: String = "",
    ): MailMessageCandidate =
        MailMessageCandidate(
            integrationId = null,
            provider = MailProvider.GOOGLE,
            accountEmail = "stub.google@example.com",
            messageId = "message-id",
            threadId = "thread-id",
            subject = subject,
            from = "recruit@example.com",
            receivedAt = Instant.parse("2026-05-06T00:00:00Z"),
            snippet = snippet,
        )

    private fun defaultStages(): List<StageCandidate> =
        listOf(
            StageCandidate(id = 1L, name = "서류", category = StageCategory.IN_PROGRESS),
            StageCandidate(id = 2L, name = "코딩테스트", category = StageCategory.IN_PROGRESS),
            StageCandidate(id = 3L, name = "최종 합격", category = StageCategory.PASSED),
            StageCandidate(id = 4L, name = "불합격", category = StageCategory.REJECTED),
        )
}

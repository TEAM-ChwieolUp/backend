package com.cheerup.demo.mail.classifier

import com.cheerup.demo.ai.client.AiServerException
import com.cheerup.demo.ai.client.AiServerProperties
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.mail.client.MailMessageCandidate
import com.cheerup.demo.mail.domain.MailProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant

class AiRecruitmentMailClassifierTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var classifier: AiRecruitmentMailClassifier

    @BeforeEach
    fun setUp() {
        val properties = AiServerProperties().apply {
            baseUrl = "https://ai.example.com"
            mailStageClassifyPath = "/ai/mail/stage-classify"
        }
        val builder = RestClient.builder().baseUrl(properties.requiredBaseUrl())
        server = MockRestServiceServer.bindTo(builder).build()
        classifier = AiRecruitmentMailClassifier(builder.build(), properties)
    }

    @Test
    fun `classifies mail using only supplied stages`() {
        server.expect(requestTo("https://ai.example.com/ai/mail/stage-classify"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.mail_subject").value("[카카오] 1차 인터뷰"))
            .andExpect(jsonPath("$.mail_body").value("전체 메일 본문"))
            .andExpect(jsonPath("$.user_stage_categories[0].id").value(3))
            .andExpect(jsonPath("$.user_stage_categories[0].order").value(2))
            .andRespond(
                withSuccess(
                    """
                    {
                      "predicted_stage": {"id": 3, "name": "1차 면접", "order": 2},
                      "confidence": 0.91,
                      "reason": "1차 인터뷰가 명시되어 있습니다.",
                      "evidence": ["1차 인터뷰"],
                      "needs_user_confirmation": true
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = classifier.classify(command())

        assertThat(result.recommendedStageId).isEqualTo(3L)
        assertThat(result.stageCategory).isEqualTo(StageCategory.IN_PROGRESS)
        assertThat(result.evidence).containsExactly("1차 인터뷰")
        assertThat(result.needsUserConfirmation).isTrue()
        server.verify()
    }

    @Test
    fun `rejects stage that was not supplied`() {
        server.expect(requestTo("https://ai.example.com/ai/mail/stage-classify"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "predicted_stage": {"id": 999, "name": "알 수 없음"},
                      "confidence": 0.8,
                      "reason": "reason",
                      "evidence": [],
                      "needs_user_confirmation": true
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { classifier.classify(command()) }
            .isInstanceOf(AiServerException::class.java)
    }

    private fun command() =
        RecruitmentMailClassificationCommand(
            message = MailMessageCandidate(
                integrationId = 1L,
                provider = MailProvider.GOOGLE,
                accountEmail = "user@example.com",
                messageId = "message-1",
                threadId = "thread-1",
                subject = "[카카오] 1차 인터뷰",
                from = "recruit@example.com",
                receivedAt = Instant.EPOCH,
                snippet = "snippet",
            ),
            stages = listOf(
                StageCandidate(
                    id = 3L,
                    name = "1차 면접",
                    category = StageCategory.IN_PROGRESS,
                    order = 2,
                ),
            ),
            mailBody = "전체 메일 본문",
        )
}

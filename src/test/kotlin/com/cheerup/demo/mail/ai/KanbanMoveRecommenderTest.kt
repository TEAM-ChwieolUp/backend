package com.cheerup.demo.mail.ai

import com.cheerup.demo.ai.client.AiServerException
import com.cheerup.demo.ai.client.AiServerProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class KanbanMoveRecommenderTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var recommender: KanbanMoveRecommender

    @BeforeEach
    fun setUp() {
        val properties = AiServerProperties().apply { baseUrl = "https://ai.example.com" }
        val builder = RestClient.builder().baseUrl(properties.requiredBaseUrl())
        server = MockRestServiceServer.bindTo(builder).build()
        recommender = KanbanMoveRecommender(builder.build(), properties)
    }

    @Test
    fun `maps actionable recommendation`() {
        server.expect(requestTo("https://ai.example.com/ai/kanban/move-recommend"))
            .andExpect(jsonPath("$.current_kanban_stage.id").value(2))
            .andExpect(jsonPath("$.user_kanban_stages[1].id").value(4))
            .andRespond(
                withSuccess(
                    """
                    {
                      "recommend_move": true,
                      "from_stage": {"id": 2, "name": "코딩테스트", "order": 2},
                      "to_stage": {"id": 4, "name": "최종 면접", "order": 4},
                      "confidence": 0.88,
                      "reason": "최종 인터뷰 안내",
                      "evidence": ["최종 인터뷰"],
                      "needs_user_confirmation": true
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = recommender.recommend(request())

        assertThat(response.recommendMove).isTrue()
        assertThat(response.toStage?.id).isEqualTo(4L)
    }

    @Test
    fun `rejects actionable response without target stage`() {
        server.expect(requestTo("https://ai.example.com/ai/kanban/move-recommend"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "recommend_move": true,
                      "from_stage": {"id": 2, "name": "코딩테스트"},
                      "confidence": 0.88,
                      "reason": "최종 인터뷰 안내",
                      "evidence": ["최종 인터뷰"],
                      "needs_user_confirmation": true
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { recommender.recommend(request()) }
            .isInstanceOf(AiServerException::class.java)
    }

    private fun request() =
        KanbanMoveRecommendRequest(
            mailSubject = "최종 인터뷰",
            mailBody = "최종 인터뷰 일정을 안내드립니다.",
            currentKanbanStage = AiStage(2, "코딩테스트", order = 2),
            userKanbanStages = listOf(
                AiStage(2, "코딩테스트", order = 2),
                AiStage(4, "최종 면접", order = 4),
            ),
        )
}

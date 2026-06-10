package com.cheerup.demo.retrospective.ai

import com.cheerup.demo.ai.client.AiServerProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException

class ExternalRetrospectiveQuestionGeneratorTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var generator: ExternalRetrospectiveQuestionGenerator
    private lateinit var properties: AiServerProperties

    @BeforeEach
    fun setUp() {
        properties = AiServerProperties().apply {
            baseUrl = "https://ai.example.com"
            retrospectiveQuestionsPath = "/custom/retrospective/questions"
        }
        val builder = RestClient.builder().baseUrl(properties.requiredBaseUrl())
        server = MockRestServiceServer.bindTo(builder).build()
        generator = ExternalRetrospectiveQuestionGenerator(
            restClient = builder.build(),
            properties = properties,
        )
    }

    @Test
    fun `generate posts snake case request without auth headers and maps response`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(headerDoesNotExist("X-Internal-Api-Key"))
            .andExpect(jsonPath("$.user_id").value(99))
            .andExpect(jsonPath("$.job_posting_title").value("Acme Backend Hiring"))
            .andExpect(jsonPath("$.company_name").value("Acme"))
            .andExpect(jsonPath("$.job_role").value("Backend"))
            .andExpect(jsonPath("$.process_stage").value("Interview"))
            .andExpect(jsonPath("$.question_count").value(4))
            .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON))

        val result = generator.generate(context())

        assertThat(result.questionSetTitle).isEqualTo("Interview retrospective")
        assertThat(result.jobRole).isEqualTo("Backend")
        assertThat(result.processStage).isEqualTo("Interview")
        assertThat(result.questions).hasSize(1)
        val question = result.questions.single()
        assertThat(question.category).isEqualTo("technical_depth")
        assertThat(question.question).isEqualTo("What should be improved?")
        assertThat(question.reason).isEqualTo("Find improvement points.")
        assertThat(question.priority).isEqualTo("high")
        assertThat(question.sourceTemplateIds).containsExactly("q_backend_interview_001")
        server.verify()
    }

    @Test
    fun `generate maps HTTP 4xx to generation failure`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST))

        assertThatThrownBy { generator.generate(context()) }
            .isInstanceOf(RetrospectiveQuestionGenerationException::class.java)
    }

    @Test
    fun `generate maps HTTP 5xx to generation failure`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andRespond(withServerError())

        assertThatThrownBy { generator.generate(context()) }
            .isInstanceOf(RetrospectiveQuestionGenerationException::class.java)
    }

    @Test
    fun `generate maps timeout to timeout exception`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andRespond(withException(SocketTimeoutException("read timed out")))

        assertThatThrownBy { generator.generate(context()) }
            .isInstanceOf(RetrospectiveQuestionTimeoutException::class.java)
    }

    @Test
    fun `generate maps invalid json to generation failure`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON))

        assertThatThrownBy { generator.generate(context()) }
            .isInstanceOf(RetrospectiveQuestionGenerationException::class.java)
    }

    @Test
    fun `generate maps missing required fields to generation failure`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "job_role": "Backend",
                      "process_stage": "Interview",
                      "questions": []
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { generator.generate(context()) }
            .isInstanceOf(RetrospectiveQuestionGenerationException::class.java)
    }

    @Test
    fun `generate maps empty questions to generation failure`() {
        server.expect(requestTo("https://ai.example.com/custom/retrospective/questions"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "question_set_title": "Interview retrospective",
                      "job_role": "Backend",
                      "process_stage": "Interview",
                      "questions": []
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { generator.generate(context()) }
            .isInstanceOf(RetrospectiveQuestionGenerationException::class.java)
    }

    private fun context(): RetrospectiveQuestionContext =
        RetrospectiveQuestionContext(
            userId = 99L,
            jobPostingTitle = "Acme Backend Hiring",
            companyName = "Acme",
            jobRole = "Backend",
            processStage = "Interview",
            questionCount = 4,
        )

    private fun successResponse(): String =
        """
        {
          "question_set_title": "Interview retrospective",
          "job_role": "Backend",
          "process_stage": "Interview",
          "questions": [
            {
              "category": "technical_depth",
              "question": "What should be improved?",
              "reason": "Find improvement points.",
              "priority": "high",
              "source_template_ids": ["q_backend_interview_001"]
            }
          ]
        }
        """.trimIndent()
}

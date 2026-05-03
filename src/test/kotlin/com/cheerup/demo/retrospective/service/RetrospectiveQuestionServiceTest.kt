package com.cheerup.demo.retrospective.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionContext
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerationException
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerator
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionTimeoutException
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RetrospectiveQuestionServiceTest {

    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var stageRepository: StageRepository
    private lateinit var questionGenerator: RetrospectiveQuestionGenerator
    private lateinit var rateLimiter: RetrospectiveAiRateLimiter
    private lateinit var service: RetrospectiveQuestionService

    private val userId = 99L
    private val applicationId = 101L
    private val stageId = 5L

    @BeforeEach
    fun setUp() {
        applicationRepository = mockk()
        stageRepository = mockk()
        questionGenerator = mockk()
        rateLimiter = mockk()
        service = RetrospectiveQuestionService(
            applicationRepository = applicationRepository,
            stageRepository = stageRepository,
            questionGenerator = questionGenerator,
            rateLimiter = rateLimiter,
        )
    }

    @Test
    fun generateQuestions_success_filtersAndLimitsGeneratedQuestions() {
        val contextSlot = slot<RetrospectiveQuestionContext>()
        val tooLong = "x".repeat(1001)
        val generated = listOf("  first question  ", "first question", "", tooLong) +
            (1..20).map { "question $it" }

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(capture(contextSlot)) } returns generated

        val response = service.generateQuestions(
            userId = userId,
            request = RetrospectiveQuestionRequest(applicationId = applicationId, stageId = stageId),
        )

        assertThat(response.questions).hasSize(15)
        assertThat(response.questions.first()).isEqualTo("first question")
        assertThat(response.questions).doesNotContain("", tooLong)
        assertThat(contextSlot.captured.companyName).isEqualTo("Acme")
        assertThat(contextSlot.captured.position).isEqualTo("Backend")
        assertThat(contextSlot.captured.memo).isEqualTo("memo")
        assertThat(contextSlot.captured.stageName).isEqualTo("1st interview")
        assertThat(contextSlot.captured.stageCategory).isEqualTo(StageCategory.IN_PROGRESS)
    }

    @Test
    fun generateQuestions_applicationNotFound() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns null

        assertThatThrownBy {
            service.generateQuestions(userId, RetrospectiveQuestionRequest(applicationId = applicationId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.APPLICATION_NOT_FOUND }

        verify(exactly = 0) { rateLimiter.tryAcquire(any()) }
        verify(exactly = 0) { questionGenerator.generate(any()) }
    }

    @Test
    fun generateQuestions_stageNotFound() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns null

        assertThatThrownBy {
            service.generateQuestions(
                userId = userId,
                request = RetrospectiveQuestionRequest(applicationId = applicationId, stageId = stageId),
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.STAGE_NOT_FOUND }

        verify(exactly = 0) { rateLimiter.tryAcquire(any()) }
        verify(exactly = 0) { questionGenerator.generate(any()) }
    }

    @Test
    fun generateQuestions_rateLimited() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { rateLimiter.tryAcquire(userId) } returns false

        assertThatThrownBy {
            service.generateQuestions(userId, RetrospectiveQuestionRequest(applicationId = applicationId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.RATE_LIMITED }

        verify(exactly = 0) { questionGenerator.generate(any()) }
    }

    @Test
    fun generateQuestions_generatorFailure() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(any()) } throws RetrospectiveQuestionGenerationException("bad response")

        assertThatThrownBy {
            service.generateQuestions(userId, RetrospectiveQuestionRequest(applicationId = applicationId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.AI_GENERATION_FAILED }
    }

    @Test
    fun generateQuestions_generatorTimeout() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(any()) } throws RetrospectiveQuestionTimeoutException("timeout")

        assertThatThrownBy {
            service.generateQuestions(userId, RetrospectiveQuestionRequest(applicationId = applicationId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.AI_GENERATION_TIMEOUT }
    }

    @Test
    fun generateQuestions_noValidQuestions() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(any()) } returns listOf("", " ", "x".repeat(1001))

        assertThatThrownBy {
            service.generateQuestions(userId, RetrospectiveQuestionRequest(applicationId = applicationId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.AI_GENERATION_FAILED }
    }

    private fun fixtureApplication(): Application =
        Application(
            userId = userId,
            stageId = stageId,
            companyName = "Acme",
            position = "Backend",
            priority = Priority.NORMAL,
            memo = "memo",
        )

    private fun fixtureStage(): Stage =
        Stage(
            userId = userId,
            name = "1st interview",
            displayOrder = 1,
            color = "#0EA5E9",
            category = StageCategory.IN_PROGRESS,
        )
}

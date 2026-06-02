package com.cheerup.demo.retrospective.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.ai.GeneratedRetrospectiveQuestion
import com.cheerup.demo.retrospective.ai.RetrospectiveAiProperties
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionContext
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerationException
import com.cheerup.demo.retrospective.ai.RetrospectiveQuestionGenerationResult
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
    private lateinit var properties: RetrospectiveAiProperties
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
        properties = RetrospectiveAiProperties().apply {
            defaultQuestionCount = 4
            maxQuestionCount = 15
            dailyLimit = 50
        }
        service = RetrospectiveQuestionService(
            applicationRepository = applicationRepository,
            stageRepository = stageRepository,
            questionGenerator = questionGenerator,
            rateLimiter = rateLimiter,
            properties = properties,
        )
    }

    @Test
    fun generateQuestions_success_usesDefaultQuestionCountAndApplicationStageFallback() {
        val contextSlot = slot<RetrospectiveQuestionContext>()

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(capture(contextSlot)) } returns fixtureResult()

        val response = service.generateQuestions(
            userId = userId,
            request = RetrospectiveQuestionRequest(applicationId = applicationId),
        )

        assertThat(response.questionSetTitle).isEqualTo("Interview retrospective")
        assertThat(response.jobRole).isEqualTo("Backend")
        assertThat(response.processStage).isEqualTo("1st interview")
        assertThat(response.questions).hasSize(1)
        assertThat(response.questions.single().sourceTemplateIds).containsExactly("q_backend_interview_001")
        assertThat(contextSlot.captured.userId).isEqualTo(userId)
        assertThat(contextSlot.captured.jobPostingTitle).isEqualTo("Acme Backend 채용")
        assertThat(contextSlot.captured.companyName).isEqualTo("Acme")
        assertThat(contextSlot.captured.jobRole).isEqualTo("Backend")
        assertThat(contextSlot.captured.processStage).isEqualTo("1st interview")
        assertThat(contextSlot.captured.questionCount).isEqualTo(4)
    }

    @Test
    fun generateQuestions_success_usesExplicitStageAndQuestionCount() {
        val explicitStageId = 8L
        val contextSlot = slot<RetrospectiveQuestionContext>()

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication(stageId = stageId)
        every { stageRepository.findByIdAndUserId(explicitStageId, userId) } returns
            fixtureStage(name = "coding test")
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(capture(contextSlot)) } returns fixtureResult(processStage = "coding test")

        service.generateQuestions(
            userId = userId,
            request = RetrospectiveQuestionRequest(
                applicationId = applicationId,
                stageId = explicitStageId,
                questionCount = 7,
            ),
        )

        assertThat(contextSlot.captured.processStage).isEqualTo("coding test")
        assertThat(contextSlot.captured.questionCount).isEqualTo(7)
        verify(exactly = 0) { stageRepository.findByIdAndUserId(stageId, userId) }
    }

    @Test
    fun generateQuestions_success_usesDefaultProcessStageWhenFallbackStageMissing() {
        val contextSlot = slot<RetrospectiveQuestionContext>()

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns null
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(capture(contextSlot)) } returns fixtureResult(processStage = "전체 전형")

        service.generateQuestions(
            userId = userId,
            request = RetrospectiveQuestionRequest(applicationId = applicationId),
        )

        assertThat(contextSlot.captured.processStage).isEqualTo("전체 전형")
    }

    @Test
    fun generateQuestions_filtersInvalidQuestionsAndCapsByConfiguredMax() {
        properties.maxQuestionCount = 2
        val tooLong = "x".repeat(1001)
        val generatedQuestions = listOf(
            fixtureQuestion(question = "first"),
            fixtureQuestion(question = "first"),
            fixtureQuestion(question = ""),
            fixtureQuestion(question = tooLong),
            fixtureQuestion(question = "second"),
            fixtureQuestion(question = "third"),
        )

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(any()) } returns fixtureResult(questions = generatedQuestions)

        val response = service.generateQuestions(
            userId = userId,
            request = RetrospectiveQuestionRequest(applicationId = applicationId, questionCount = 2),
        )

        assertThat(response.questions.map { it.question }).containsExactly("first", "second")
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
    fun generateQuestions_explicitStageNotFound() {
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
    fun generateQuestions_questionCountOutOfRange() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()

        assertThatThrownBy {
            service.generateQuestions(
                userId = userId,
                request = RetrospectiveQuestionRequest(applicationId = applicationId, questionCount = 16),
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.INVALID_INPUT }

        verify(exactly = 0) { rateLimiter.tryAcquire(any()) }
        verify(exactly = 0) { questionGenerator.generate(any()) }
    }

    @Test
    fun generateQuestions_rateLimited() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns fixtureApplication()
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
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
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
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
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
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
        every { stageRepository.findByIdAndUserId(stageId, userId) } returns fixtureStage()
        every { rateLimiter.tryAcquire(userId) } returns true
        every { questionGenerator.generate(any()) } returns fixtureResult(
            questions = listOf(
                fixtureQuestion(question = ""),
                fixtureQuestion(question = "x".repeat(1001)),
            ),
        )

        assertThatThrownBy {
            service.generateQuestions(userId, RetrospectiveQuestionRequest(applicationId = applicationId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.AI_GENERATION_FAILED }
    }

    private fun fixtureApplication(stageId: Long = this.stageId): Application =
        Application(
            userId = userId,
            stageId = stageId,
            companyName = "Acme",
            position = "Backend",
            priority = Priority.NORMAL,
            memo = "memo",
        )

    private fun fixtureStage(name: String = "1st interview"): Stage =
        Stage(
            userId = userId,
            name = name,
            displayOrder = 1,
            color = "#0EA5E9",
            category = StageCategory.IN_PROGRESS,
        )

    private fun fixtureResult(
        processStage: String = "1st interview",
        questions: List<GeneratedRetrospectiveQuestion> = listOf(fixtureQuestion()),
    ): RetrospectiveQuestionGenerationResult =
        RetrospectiveQuestionGenerationResult(
            questionSetTitle = "Interview retrospective",
            jobRole = "Backend",
            processStage = processStage,
            questions = questions,
        )

    private fun fixtureQuestion(question: String = "What should be improved?"): GeneratedRetrospectiveQuestion =
        GeneratedRetrospectiveQuestion(
            category = "technical_depth",
            question = question,
            reason = "Find improvement points.",
            priority = "high",
            sourceTemplateIds = listOf("q_backend_interview_001"),
        )
}

package com.cheerup.demo.mail.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.application.service.ApplicationService
import com.cheerup.demo.global.base.BaseEntity
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.ai.MailAiAnalysisCoordinator
import com.cheerup.demo.mail.client.MailClientRegistry
import com.cheerup.demo.mail.domain.MailSuggestion
import com.cheerup.demo.mail.domain.MailSuggestionStatus
import com.cheerup.demo.mail.oauth.MailTokenCipher
import com.cheerup.demo.mail.repository.MailIntegrationRepository
import com.cheerup.demo.mail.repository.MailSuggestionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MailSuggestionServiceTest {
    private val integrationRepository = mockk<MailIntegrationRepository>()
    private val suggestionRepository = mockk<MailSuggestionRepository>()
    private val applicationRepository = mockk<ApplicationRepository>()
    private val stageRepository = mockk<StageRepository>()
    private val mailClientRegistry = mockk<MailClientRegistry>()
    private val mailTokenCipher = mockk<MailTokenCipher>()
    private val aiAnalysisCoordinator = mockk<MailAiAnalysisCoordinator>()
    private val applicationService = mockk<ApplicationService>()
    private val now = Instant.parse("2026-06-09T00:00:00Z")
    private lateinit var service: MailSuggestionService

    @BeforeEach
    fun setUp() {
        service = MailSuggestionService(
            integrationRepository,
            suggestionRepository,
            applicationRepository,
            stageRepository,
            mailClientRegistry,
            mailTokenCipher,
            aiAnalysisCoordinator,
            applicationService,
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `accept moves application before marking suggestion accepted`() {
        val suggestion = persistedSuggestion()
        every { suggestionRepository.findByIdAndUserId(10L, 1L) } returns suggestion
        every {
            applicationService.applyAiStageSuggestion(
                userId = 1L,
                applicationId = 20L,
                expectedFromStageId = 2L,
                toStageId = 3L,
            )
        } returns mockk()
        every { suggestionRepository.saveAndFlush(suggestion) } returns suggestion

        val response = service.accept(userId = 1L, suggestionId = 10L)

        assertThat(response.status).isEqualTo(MailSuggestionStatus.ACCEPTED)
        assertThat(response.processedAt).isEqualTo(now)
        verify(exactly = 1) {
            applicationService.applyAiStageSuggestion(1L, 20L, 2L, 3L)
            suggestionRepository.saveAndFlush(suggestion)
        }
    }

    @Test
    fun `failed card move leaves suggestion pending`() {
        val suggestion = persistedSuggestion()
        every { suggestionRepository.findByIdAndUserId(10L, 1L) } returns suggestion
        every {
            applicationService.applyAiStageSuggestion(1L, 20L, 2L, 3L)
        } throws BusinessException(ErrorCode.SUGGESTION_STALE)

        assertThatThrownBy { service.accept(userId = 1L, suggestionId = 10L) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.SUGGESTION_STALE }

        assertThat(suggestion.status).isEqualTo(MailSuggestionStatus.PENDING)
        verify(exactly = 0) { suggestionRepository.saveAndFlush(any()) }
    }

    @Test
    fun `processed suggestion cannot be accepted again`() {
        val suggestion = persistedSuggestion(status = MailSuggestionStatus.REJECTED)
        every { suggestionRepository.findByIdAndUserId(10L, 1L) } returns suggestion

        assertThatThrownBy { service.accept(userId = 1L, suggestionId = 10L) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.SUGGESTION_ALREADY_PROCESSED }

        verify(exactly = 0) { applicationService.applyAiStageSuggestion(any(), any(), any(), any()) }
    }

    private fun persistedSuggestion(
        status: MailSuggestionStatus = MailSuggestionStatus.PENDING,
    ): MailSuggestion {
        val suggestion = MailSuggestion(
            userId = 1L,
            integrationId = 5L,
            messageId = "message-1",
            applicationId = 20L,
            predictedStageId = 3L,
            predictedStageName = "1차 면접",
            classificationConfidence = 0.9,
            classificationReason = "면접 안내",
            fromStageId = 2L,
            fromStageName = "코딩테스트",
            toStageId = 3L,
            toStageName = "1차 면접",
            moveConfidence = 0.88,
            moveReason = "이동 추천",
            status = status,
        )
        MailSuggestion::class.java.getDeclaredField("id").apply {
            isAccessible = true
            set(suggestion, 10L)
        }
        BaseEntity::class.java.getDeclaredField("createdAt").apply {
            isAccessible = true
            set(suggestion, now)
        }
        BaseEntity::class.java.getDeclaredField("updatedAt").apply {
            isAccessible = true
            set(suggestion, now)
        }
        return suggestion
    }
}

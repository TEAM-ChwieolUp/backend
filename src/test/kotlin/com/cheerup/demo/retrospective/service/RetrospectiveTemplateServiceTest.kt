package com.cheerup.demo.retrospective.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.domain.Retrospective
import com.cheerup.demo.retrospective.domain.RetrospectiveItem
import com.cheerup.demo.retrospective.domain.RetrospectiveTemplate
import com.cheerup.demo.retrospective.dto.ApplyRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.repository.RetrospectiveRepository
import com.cheerup.demo.retrospective.repository.RetrospectiveTemplateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class RetrospectiveTemplateServiceTest {

    private lateinit var templateRepository: RetrospectiveTemplateRepository
    private lateinit var retrospectiveRepository: RetrospectiveRepository
    private lateinit var service: RetrospectiveTemplateService

    private val userId = 99L
    private val templateId = 3L
    private val retrospectiveId = 12L

    @BeforeEach
    fun setUp() {
        templateRepository = mockk(relaxUnitFun = true)
        retrospectiveRepository = mockk(relaxUnitFun = true)
        service = RetrospectiveTemplateService(
            templateRepository,
            retrospectiveRepository,
            // self-injection 자리. 객체 생성 직후 ReflectionTestUtils 로 self=service 로 다시 묶는다.
            self = mockk(relaxed = true),
        )
        ReflectionTestUtils.setField(service, "self", service)
    }

    @Test
    @DisplayName("create - 질문 공백 항목을 필터링하고 저장한다")
    fun create_filtersBlankQuestions() {
        every { templateRepository.existsByUserIdAndName(userId, "Interview") } returns false

        val savedSlot = slot<RetrospectiveTemplate>()
        every { templateRepository.saveAndFlush(capture(savedSlot)) } answers {
            savedSlot.captured.also { ReflectionTestUtils.setField(it, "id", templateId) }
        }

        val response = service.create(
            userId,
            CreateRetrospectiveTemplateRequest(
                name = " Interview ",
                questions = listOf(" good point? ", " ", "next action?"),
            ),
        )

        assertThat(response.id).isEqualTo(templateId)
        assertThat(response.name).isEqualTo("Interview")
        assertThat(response.questions).containsExactly("good point?", "next action?")
        assertThat(savedSlot.captured.questions).containsExactly("good point?", "next action?")
    }

    @Test
    @DisplayName("create - 같은 이름이 이미 있으면 RETROSPECTIVE_TEMPLATE_DUPLICATE")
    fun create_duplicateName() {
        every { templateRepository.existsByUserIdAndName(userId, "Interview") } returns true

        assertThatThrownBy {
            service.create(userId, CreateRetrospectiveTemplateRequest("Interview", emptyList()))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE }

        verify(exactly = 0) { templateRepository.saveAndFlush(any<RetrospectiveTemplate>()) }
    }

    @Test
    @DisplayName("update - 이름 변경이 중복이면 RETROSPECTIVE_TEMPLATE_DUPLICATE")
    fun update_duplicateName() {
        val existing = template(name = "Old")
        every { templateRepository.findByIdAndUserId(templateId, userId) } returns existing
        every {
            templateRepository.existsByUserIdAndNameAndIdNot(userId, "Interview", templateId)
        } returns true

        assertThatThrownBy {
            service.update(userId, templateId, UpdateRetrospectiveTemplateRequest(name = "Interview"))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE }

        verify(exactly = 0) { templateRepository.saveAndFlush(any<RetrospectiveTemplate>()) }
    }

    @Test
    @DisplayName("applyTemplate - 템플릿 질문을 회고 items 끝에 answer=null로 append")
    fun applyTemplate_appendsQuestions() {
        val retrospective = retrospective(
            items = mutableListOf(RetrospectiveItem(question = "existing", answer = "done")),
        )
        val template = template(
            questions = mutableListOf(" first ", "", "second"),
        )

        every { retrospectiveRepository.findByIdAndUserId(retrospectiveId, userId) } returns retrospective
        every { templateRepository.findByIdAndUserId(templateId, userId) } returns template
        every { retrospectiveRepository.saveAndFlush(any<Retrospective>()) } answers { firstArg() }

        val response = service.applyTemplate(
            userId,
            retrospectiveId,
            ApplyRetrospectiveTemplateRequest(templateId),
        )

        assertThat(response.items).hasSize(3)
        assertThat(response.items.map { it.question }).containsExactly("existing", "first", "second")
        assertThat(response.items[1].answer).isNull()
        assertThat(response.items[2].answer).isNull()
    }

    @Test
    @DisplayName("applyTemplate - 다른 사용자 템플릿이면 RETROSPECTIVE_TEMPLATE_NOT_FOUND")
    fun applyTemplate_templateNotFound() {
        every { retrospectiveRepository.findByIdAndUserId(retrospectiveId, userId) } returns retrospective()
        every { templateRepository.findByIdAndUserId(templateId, userId) } returns null

        assertThatThrownBy {
            service.applyTemplate(userId, retrospectiveId, ApplyRetrospectiveTemplateRequest(templateId))
        }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND }
    }

    private fun template(
        id: Long = templateId,
        name: String = "Interview",
        questions: MutableList<String> = mutableListOf("one", "two"),
    ): RetrospectiveTemplate =
        RetrospectiveTemplate(
            userId = userId,
            name = name,
            questions = questions,
        ).also {
            ReflectionTestUtils.setField(it, "id", id)
        }

    private fun retrospective(
        id: Long = retrospectiveId,
        items: MutableList<RetrospectiveItem> = mutableListOf(),
    ): Retrospective =
        Retrospective(
            userId = userId,
            applicationId = 101L,
            stageId = 5L,
            items = items,
        ).also {
            ReflectionTestUtils.setField(it, "id", id)
            ReflectionTestUtils.setField(it, "createdAt", Instant.parse("2026-05-01T00:00:00Z"))
            ReflectionTestUtils.setField(it, "updatedAt", Instant.parse("2026-05-01T01:00:00Z"))
        }
}

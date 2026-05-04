package com.cheerup.demo.retrospective.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.domain.RetrospectiveTemplate
import com.cheerup.demo.retrospective.dto.ApplyRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveTemplateResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.toResponse
import com.cheerup.demo.retrospective.repository.RetrospectiveRepository
import com.cheerup.demo.retrospective.repository.RetrospectiveTemplateRepository
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RetrospectiveTemplateService(
    private val retrospectiveTemplateRepository: RetrospectiveTemplateRepository,
    private val retrospectiveRepository: RetrospectiveRepository,
    // self-injection: applyTemplate 의 변경 트랜잭션을 재시도 루프 안에서 새 트랜잭션으로 호출하기 위함.
    @Lazy private val self: RetrospectiveTemplateService,
) {

    fun list(userId: Long): List<RetrospectiveTemplateResponse> =
        retrospectiveTemplateRepository.findAllByUserIdOrderByIdAsc(userId)
            .map { it.toResponse() }

    fun get(userId: Long, templateId: Long): RetrospectiveTemplateResponse =
        findOwnedTemplate(userId, templateId).toResponse()

    @Transactional
    fun create(
        userId: Long,
        request: CreateRetrospectiveTemplateRequest,
    ): RetrospectiveTemplateResponse {
        val name = normalizeName(request.name)
        val questions = normalizeQuestions(request.questions)

        if (retrospectiveTemplateRepository.existsByUserIdAndName(userId, name)) {
            duplicateTemplate(name)
        }

        val template = RetrospectiveTemplate(
            userId = userId,
            name = name,
            questions = questions.toMutableList(),
        )

        return saveOrTranslateDuplicate(template).toResponse()
    }

    @Transactional
    fun update(
        userId: Long,
        templateId: Long,
        request: UpdateRetrospectiveTemplateRequest,
    ): RetrospectiveTemplateResponse {
        val template = findOwnedTemplate(userId, templateId)

        if (request.isEmpty()) {
            return template.toResponse()
        }

        val nextName = request.name?.let { normalizeName(it) } ?: template.name
        val nextQuestions = request.questions?.let { normalizeQuestions(it) } ?: template.questions

        if (nextName != template.name &&
            retrospectiveTemplateRepository.existsByUserIdAndNameAndIdNot(userId, nextName, templateId)
        ) {
            duplicateTemplate(nextName)
        }

        template.update(nextName, nextQuestions)

        return try {
            retrospectiveTemplateRepository.saveAndFlush(template).toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(
                ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE,
                detail = "name=${template.name}",
                cause = e,
            )
        }
    }

    @Transactional
    fun delete(userId: Long, templateId: Long) {
        val template = findOwnedTemplate(userId, templateId)
        retrospectiveTemplateRepository.delete(template)
    }

    fun applyTemplate(
        userId: Long,
        retrospectiveId: Long,
        request: ApplyRetrospectiveTemplateRequest,
    ): RetrospectiveResponse =
        retryOnRetrospectiveOptimisticLock(retrospectiveId) {
            self.applyTemplateTx(userId, retrospectiveId, request)
        }

    @Transactional
    fun applyTemplateTx(
        userId: Long,
        retrospectiveId: Long,
        request: ApplyRetrospectiveTemplateRequest,
    ): RetrospectiveResponse {
        val retrospective = retrospectiveRepository.findByIdAndUserId(retrospectiveId, userId)
            ?: throw BusinessException(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                detail = "retrospectiveId=$retrospectiveId",
            )
        val template = findOwnedTemplate(userId, request.templateId)

        retrospective.appendQuestions(template.questions)

        // saveAndFlush — 트랜잭션 내에서 즉시 flush 하여 @Version 충돌을
        // OptimisticLockingFailureException 으로 표면화하여 재시도 가능하게 한다.
        return retrospectiveRepository.saveAndFlush(retrospective).toResponse()
    }

    private fun findOwnedTemplate(userId: Long, templateId: Long): RetrospectiveTemplate =
        retrospectiveTemplateRepository.findByIdAndUserId(templateId, userId)
            ?: throw BusinessException(
                ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND,
                detail = "templateId=$templateId",
            )

    private fun normalizeName(name: String): String {
        val normalized = name.trim()
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT, detail = "name must not be blank")
        }
        if (normalized.length > MAX_TEMPLATE_NAME_LENGTH) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                detail = "name must be $MAX_TEMPLATE_NAME_LENGTH characters or less",
            )
        }
        return normalized
    }

    private fun normalizeQuestions(questions: List<String>): List<String> {
        val normalized = questions
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (normalized.size > MAX_TEMPLATE_QUESTION_COUNT) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                detail = "questions size must be $MAX_TEMPLATE_QUESTION_COUNT or less",
            )
        }
        if (normalized.any { it.length > MAX_QUESTION_LENGTH }) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                detail = "question must be $MAX_QUESTION_LENGTH characters or less",
            )
        }

        return normalized
    }

    private fun saveOrTranslateDuplicate(template: RetrospectiveTemplate): RetrospectiveTemplate =
        try {
            retrospectiveTemplateRepository.saveAndFlush(template)
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(
                ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE,
                detail = "name=${template.name}",
                cause = e,
            )
        }

    private fun duplicateTemplate(name: String): Nothing =
        throw BusinessException(ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE, detail = "name=$name")

    companion object {
        private const val MAX_TEMPLATE_NAME_LENGTH = 50
        private const val MAX_TEMPLATE_QUESTION_COUNT = 50
        private const val MAX_QUESTION_LENGTH = 1000
    }
}

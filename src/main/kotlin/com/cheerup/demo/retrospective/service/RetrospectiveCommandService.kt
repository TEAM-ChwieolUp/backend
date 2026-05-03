package com.cheerup.demo.retrospective.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.domain.Retrospective
import com.cheerup.demo.retrospective.domain.RetrospectiveItem
import com.cheerup.demo.retrospective.dto.AddRetrospectiveItemRequest
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveItemsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveItemRequest
import com.cheerup.demo.retrospective.dto.toItemsResponse
import com.cheerup.demo.retrospective.dto.toResponse
import com.cheerup.demo.retrospective.repository.RetrospectiveRepository
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RetrospectiveCommandService(
    private val retrospectiveRepository: RetrospectiveRepository,
    private val applicationRepository: ApplicationRepository,
    private val stageRepository: StageRepository,
    // self-injection: 같은 빈의 @Transactional 메서드를 재시도 루프 안에서 새 트랜잭션으로 호출하기 위함.
    @Lazy private val self: RetrospectiveCommandService,
) {

    @Transactional
    fun create(
        userId: Long,
        applicationId: Long,
        request: CreateRetrospectiveRequest,
    ): RetrospectiveResponse {
        applicationRepository.findByIdAndUserId(applicationId, userId)
            ?: throw BusinessException(
                ErrorCode.APPLICATION_NOT_FOUND,
                detail = "applicationId=$applicationId",
            )

        request.stageId?.let { stageId ->
            stageRepository.findByIdAndUserId(stageId, userId)
                ?: throw BusinessException(
                    ErrorCode.STAGE_NOT_FOUND,
                    detail = "stageId=$stageId",
                )
        }

        val retrospective = Retrospective(
            userId = userId,
            applicationId = applicationId,
            stageId = request.stageId,
        )
        return retrospectiveRepository.save(retrospective).toResponse()
    }

    @Transactional
    fun delete(userId: Long, retrospectiveId: Long) {
        val retrospective = retrospectiveRepository.findByIdAndUserId(retrospectiveId, userId)
            ?: throw BusinessException(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                detail = "retrospectiveId=$retrospectiveId",
            )
        retrospectiveRepository.delete(retrospective)
    }

    fun addItem(
        userId: Long,
        retrospectiveId: Long,
        request: AddRetrospectiveItemRequest,
    ): RetrospectiveItemsResponse =
        retryOnRetrospectiveOptimisticLock(retrospectiveId) {
            self.addItemTx(userId, retrospectiveId, request)
        }

    fun updateItem(
        userId: Long,
        retrospectiveId: Long,
        index: Int,
        request: UpdateRetrospectiveItemRequest,
    ): RetrospectiveItemsResponse =
        retryOnRetrospectiveOptimisticLock(retrospectiveId) {
            self.updateItemTx(userId, retrospectiveId, index, request)
        }

    fun removeItem(
        userId: Long,
        retrospectiveId: Long,
        index: Int,
    ): RetrospectiveItemsResponse =
        retryOnRetrospectiveOptimisticLock(retrospectiveId) {
            self.removeItemTx(userId, retrospectiveId, index)
        }

    @Transactional
    fun addItemTx(
        userId: Long,
        retrospectiveId: Long,
        request: AddRetrospectiveItemRequest,
    ): RetrospectiveItemsResponse {
        val retrospective = findOwned(userId, retrospectiveId)

        retrospective.addItem(
            RetrospectiveItem(
                question = request.question.trim(),
                answer = request.answer,
            ),
        )

        // saveAndFlush — 트랜잭션 내에서 즉시 flush하여 @Version 충돌을
        // OptimisticLockingFailureException 으로 감지·롤백한다 (재시도 가능 상태).
        return retrospectiveRepository.saveAndFlush(retrospective).toItemsResponse()
    }

    @Transactional
    fun updateItemTx(
        userId: Long,
        retrospectiveId: Long,
        index: Int,
        request: UpdateRetrospectiveItemRequest,
    ): RetrospectiveItemsResponse {
        val retrospective = findOwned(userId, retrospectiveId)

        if (index !in retrospective.items.indices) {
            throw BusinessException(
                ErrorCode.RETROSPECTIVE_ITEM_INDEX_INVALID,
                detail = "index=$index",
            )
        }

        if (request.isEmpty()) {
            return retrospective.toItemsResponse()
        }

        val current = retrospective.items[index]
        val nextQuestion = request.question?.trim()?.also {
            if (it.isBlank()) {
                throw BusinessException(
                    ErrorCode.INVALID_INPUT,
                    detail = "question must not be blank",
                )
            }
        } ?: current.question
        val nextAnswer = request.answer ?: current.answer

        retrospective.updateItem(
            index,
            RetrospectiveItem(question = nextQuestion, answer = nextAnswer),
        )

        return retrospectiveRepository.saveAndFlush(retrospective).toItemsResponse()
    }

    @Transactional
    fun removeItemTx(
        userId: Long,
        retrospectiveId: Long,
        index: Int,
    ): RetrospectiveItemsResponse {
        val retrospective = findOwned(userId, retrospectiveId)

        if (index !in retrospective.items.indices) {
            throw BusinessException(
                ErrorCode.RETROSPECTIVE_ITEM_INDEX_INVALID,
                detail = "index=$index",
            )
        }

        retrospective.removeItem(index)

        return retrospectiveRepository.saveAndFlush(retrospective).toItemsResponse()
    }

    private fun findOwned(userId: Long, retrospectiveId: Long) =
        retrospectiveRepository.findByIdAndUserId(retrospectiveId, userId)
            ?: throw BusinessException(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                detail = "retrospectiveId=$retrospectiveId",
            )
}

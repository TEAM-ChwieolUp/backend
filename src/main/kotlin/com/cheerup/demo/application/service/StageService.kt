package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.dto.CreateStageRequest
import com.cheerup.demo.application.dto.StageResponse
import com.cheerup.demo.application.dto.UpdateStageRequest
import com.cheerup.demo.application.dto.toStageResponse
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StageService(
    private val stageRepository: StageRepository,
    private val applicationRepository: ApplicationRepository,
) {

    fun list(userId: Long): List<StageResponse> =
        stageRepository.findAllByUserIdOrderByDisplayOrderAsc(userId)
            .map { it.toStageResponse() }

    @Transactional
    fun create(userId: Long, request: CreateStageRequest): StageResponse {
        val maxOrder = stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId)?.displayOrder ?: -1
        val passed = stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED)

        // 새 IN_PROGRESS 는 PASSED 자리에 들어가서 PASSED/REJECTED 를 한 칸 오른쪽으로 민다.
        // PASSED 가 없는 레거시 사용자(시드 미실행) 는 기존 "마지막 + 1" 동작으로 폴백.
        val maxAllowed = passed?.displayOrder ?: (maxOrder + 1)
        val targetOrder = request.displayOrder?.coerceAtMost(maxAllowed) ?: maxAllowed

        // 명시 위치 삽입 — targetOrder 이상의 기존 행을 +1 시프트해 자리 확보
        if (targetOrder <= maxOrder) {
            stageRepository.shiftDisplayOrder(
                userId = userId,
                from = targetOrder,
                to = maxOrder,
                delta = 1,
                excludeId = null,
            )
        }

        val stage = Stage(
            userId = userId,
            name = request.name,
            displayOrder = targetOrder,
            color = request.color,
            category = StageCategory.IN_PROGRESS,
        )
        return stageRepository.save(stage).toStageResponse()
    }

    @Transactional
    fun update(userId: Long, stageId: Long, request: UpdateStageRequest): StageResponse {
        val stage = stageRepository.findByIdAndUserId(stageId, userId)
            ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=$stageId")

        // 빈 본문 단축: 변경 없이 현재 값 반환
        if (request.isEmpty()) return stage.toStageResponse()

        request.displayOrder?.let { newOrder ->
            // 고정 Stage(PASSED/REJECTED)는 항상 가장 오른쪽 두 자리를 차지해야 한다.
            // 순서 변경을 허용하면 이 불변식이 깨지므로 거부.
            if (stage.category != StageCategory.IN_PROGRESS) {
                throw BusinessException(
                    ErrorCode.STAGE_ORDER_PROTECTED,
                    detail = "stageId=$stageId, category=${stage.category}",
                )
            }
            reorder(userId, stage, newOrder)
        }
        request.name?.let { stage.name = it }
        request.color?.let { stage.color = it }

        return stage.toStageResponse()
    }

    /**
     * IN_PROGRESS Stage를 [newOrder] 위치로 reorder. 사이 행을 +1/-1 시프트하고 대상만 newOrder로 이동.
     * 동일 위치면 no-op. PASSED/REJECTED 위치(또는 그 너머)로 이동 시도하면 STAGE_ORDER_PROTECTED.
     */
    private fun reorder(userId: Long, stage: Stage, newOrder: Int) {
        val from = stage.displayOrder
        // PASSED 가 있으면 maxAllowed = PASSED.displayOrder - 1 (그 미만에서만 이동 가능).
        // PASSED 가 없는 레거시 사용자는 max-1 까지 허용 (자기 자신 제외).
        val passed = stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED)
        val maxOrder = stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId)?.displayOrder ?: from
        val maxAllowed = passed?.displayOrder?.minus(1) ?: maxOrder

        if (newOrder > maxAllowed) {
            throw BusinessException(
                ErrorCode.STAGE_ORDER_PROTECTED,
                detail = "newOrder=$newOrder, maxAllowed=$maxAllowed",
            )
        }

        val to = newOrder
        if (from == to) return

        val stageId = requireNotNull(stage.id) { "Stage must be persisted" }
        if (to > from) {
            // 아래로 이동: (from, to] 행을 -1
            stageRepository.shiftDisplayOrder(
                userId = userId,
                from = from + 1,
                to = to,
                delta = -1,
                excludeId = stageId,
            )
        } else {
            // 위로 이동: [to, from) 행을 +1
            stageRepository.shiftDisplayOrder(
                userId = userId,
                from = to,
                to = from - 1,
                delta = 1,
                excludeId = stageId,
            )
        }
        stage.displayOrder = to
    }

    @Transactional
    fun delete(userId: Long, stageId: Long) {
        val stage = stageRepository.findByIdAndUserId(stageId, userId)
            ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=$stageId")

        if (stage.category == StageCategory.PASSED || stage.category == StageCategory.REJECTED) {
            throw BusinessException(ErrorCode.STAGE_FIXED, detail = "stageId=$stageId, category=${stage.category}")
        }

        val cardCount = applicationRepository.countByUserIdAndStageId(userId, stageId)
        if (cardCount > 0) {
            throw BusinessException(
                ErrorCode.STAGE_NOT_EMPTY,
                detail = "stageId=$stageId, cardCount=$cardCount",
            )
        }

        stageRepository.delete(stage)
    }
}

package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.repository.StageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신규 사용자에게 고정 Stage 두 개(PASSED, REJECTED)를 시드한다.
 *
 * 시드 정책: PASSED(displayOrder=0), REJECTED(displayOrder=1) 만 생성.
 * 두 Stage는 항상 보드의 가장 오른쪽 두 자리를 차지하며 삭제·순서 변경 불가.
 * 사용자가 IN_PROGRESS Stage를 추가하면 StageService.create 가 PASSED/REJECTED 를
 * 오른쪽으로 한 칸씩 시프트해 불변식을 유지한다.
 *
 * 멱등성: 이미 PASSED 가 시드된 사용자는 no-op (재시드 안전).
 */
@Service
class StageSeedService(
    private val stageRepository: StageRepository,
) {

    @Transactional
    fun seedDefault(userId: Long) {
        if (stageRepository.existsByUserIdAndCategory(userId, StageCategory.PASSED)) {
            return
        }

        stageRepository.save(
            Stage(
                userId = userId,
                name = DEFAULT_PASSED_NAME,
                displayOrder = 0,
                color = DEFAULT_PASSED_COLOR,
                category = StageCategory.PASSED,
            ),
        )
        stageRepository.save(
            Stage(
                userId = userId,
                name = DEFAULT_REJECTED_NAME,
                displayOrder = 1,
                color = DEFAULT_REJECTED_COLOR,
                category = StageCategory.REJECTED,
            ),
        )
    }

    companion object {
        private const val DEFAULT_PASSED_NAME = "최종 합격"
        private const val DEFAULT_PASSED_COLOR = "#22C55E"
        private const val DEFAULT_REJECTED_NAME = "불합격"
        private const val DEFAULT_REJECTED_COLOR = "#EF4444"
    }
}

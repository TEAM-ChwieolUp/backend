package com.cheerup.demo.retrospective.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.dao.OptimisticLockingFailureException

/**
 * Retrospective 변경 작업을 1회 자동 재시도한다.
 * 두 번째 시도도 @Version 충돌이면 RETROSPECTIVE_CONCURRENT_MODIFICATION (409) 로 변환한다.
 * block 안에서 호출되는 트랜잭션은 saveAndFlush로 충돌을 즉시 표면화해야 의미가 있다.
 */
internal inline fun <T> retryOnRetrospectiveOptimisticLock(
    retrospectiveId: Long,
    block: () -> T,
): T {
    return try {
        block()
    } catch (firstFailure: OptimisticLockingFailureException) {
        try {
            block()
        } catch (secondFailure: OptimisticLockingFailureException) {
            throw BusinessException(
                ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION,
                detail = "retrospectiveId=$retrospectiveId",
                cause = secondFailure,
            )
        }
    }
}

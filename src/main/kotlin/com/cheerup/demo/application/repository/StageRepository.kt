package com.cheerup.demo.application.repository

import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StageRepository : JpaRepository<Stage, Long> {

    fun findAllByUserIdOrderByDisplayOrderAsc(userId: Long): List<Stage>

    fun findByIdAndUserId(id: Long, userId: Long): Stage?

    fun findTopByUserIdOrderByDisplayOrderDesc(userId: Long): Stage?

    fun findByUserIdAndCategory(userId: Long, category: StageCategory): Stage?

    fun existsByUserIdAndCategory(userId: Long, category: StageCategory): Boolean

    /**
     * [from, to] 범위(포함)의 displayOrder를 [delta]만큼 시프트.
     * reorder의 사이 행 +1/-1을 단일 쿼리로 처리. excludeId는 자기 자신 제외용(reorder 시).
     */
    @Modifying
    @Query(
        """
        UPDATE Stage s
           SET s.displayOrder = s.displayOrder + :delta
         WHERE s.userId = :userId
           AND s.displayOrder BETWEEN :from AND :to
           AND (:excludeId IS NULL OR s.id <> :excludeId)
        """,
    )
    fun shiftDisplayOrder(
        @Param("userId") userId: Long,
        @Param("from") from: Int,
        @Param("to") to: Int,
        @Param("delta") delta: Int,
        @Param("excludeId") excludeId: Long?,
    ): Int
}

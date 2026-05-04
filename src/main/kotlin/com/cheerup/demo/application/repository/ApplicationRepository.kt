package com.cheerup.demo.application.repository

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApplicationRepository : JpaRepository<Application, Long> {

    fun findByIdAndUserId(id: Long, userId: Long): Application?

    fun countByUserIdAndStageId(userId: Long, stageId: Long): Long

    @Query(
        """
        select a from Application a
        where a.userId = :userId
          and (:stageId is null or a.stageId = :stageId)
          and (:priority is null or a.priority = :priority)
          and (
            :tagId is null or a.id in (
              select at.applicationId from ApplicationTag at where at.tagId = :tagId
            )
          )
        order by a.createdAt desc
        """,
    )
    fun findBoardCards(
        @Param("userId") userId: Long,
        @Param("stageId") stageId: Long?,
        @Param("tagId") tagId: Long?,
        @Param("priority") priority: Priority?,
    ): List<Application>
}

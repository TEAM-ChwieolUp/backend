package com.cheerup.demo.application.repository

import com.cheerup.demo.application.domain.ApplicationTag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApplicationTagRepository : JpaRepository<ApplicationTag, Long> {

    fun findAllByApplicationId(applicationId: Long): List<ApplicationTag>

    @Modifying(clearAutomatically = true)
    @Query("delete from ApplicationTag at where at.applicationId = :applicationId")
    fun deleteByApplicationId(@Param("applicationId") applicationId: Long)

    @Modifying(clearAutomatically = true)
    @Query(
        """
        delete from ApplicationTag at
        where at.applicationId = :applicationId
          and at.tagId in :tagIds
        """,
    )
    fun deleteByApplicationIdAndTagIdIn(
        @Param("applicationId") applicationId: Long,
        @Param("tagIds") tagIds: Collection<Long>,
    )

    @Query(
        """
        select at.applicationId as applicationId,
               t.id as tagId,
               t.name as tagName,
               t.color as tagColor
        from ApplicationTag at, Tag t
        where at.tagId = t.id
          and at.applicationId in :applicationIds
          and t.userId = :userId
        """,
    )
    fun findTagViewsByApplicationIds(
        @Param("applicationIds") applicationIds: Collection<Long>,
        @Param("userId") userId: Long,
    ): List<TagView>

    interface TagView {
        val applicationId: Long
        val tagId: Long
        val tagName: String
        val tagColor: String
    }
}

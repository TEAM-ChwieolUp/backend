package com.cheerup.demo.notification.repository

import com.cheerup.demo.notification.domain.Notification
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByIdAndUserId(id: Long, userId: Long): Notification?

    fun countByUserIdAndReadAtIsNull(userId: Long): Long

    fun existsByUserIdAndSourceTypeAndSourceIdAndRemindTypeAndScheduledAt(
        userId: Long,
        sourceType: NotificationSourceType,
        sourceId: Long,
        remindType: NotificationRemindType,
        scheduledAt: Instant,
    ): Boolean

    @Query(
        """
        select n from Notification n
        where n.userId = :userId
          and (:unreadOnly = false or n.readAt is null)
          and (:cursorId is null or n.id < :cursorId)
        order by n.id desc
        """,
    )
    fun findSlice(
        @Param("userId") userId: Long,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("cursorId") cursorId: Long?,
        pageable: Pageable,
    ): List<Notification>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Notification n
        set n.readAt = :readAt
        where n.userId = :userId
          and n.readAt is null
        """,
    )
    fun markAllAsRead(
        @Param("userId") userId: Long,
        @Param("readAt") readAt: Instant,
    ): Int
}

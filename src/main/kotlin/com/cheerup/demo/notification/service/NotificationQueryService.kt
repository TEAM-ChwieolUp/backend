package com.cheerup.demo.notification.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.notification.dto.NotificationListResponse
import com.cheerup.demo.notification.dto.UnreadNotificationCountResponse
import com.cheerup.demo.notification.dto.toResponse
import com.cheerup.demo.notification.repository.NotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationQueryService(
    private val notificationRepository: NotificationRepository,
) {

    fun getNotifications(
        userId: Long,
        unreadOnly: Boolean,
        limit: Int?,
        cursor: Long?,
    ): NotificationListResponse {
        val normalizedLimit = normalizeLimit(limit)
        val rows = notificationRepository.findSlice(
            userId = userId,
            unreadOnly = unreadOnly,
            cursorId = cursor,
            pageable = PageRequest.of(0, normalizedLimit + 1),
        )
        val hasNext = rows.size > normalizedLimit
        val page = rows.take(normalizedLimit)

        return NotificationListResponse(
            notifications = page.map { it.toResponse() },
            nextCursor = if (hasNext) page.lastOrNull()?.id else null,
        )
    }

    fun getUnreadCount(userId: Long): UnreadNotificationCountResponse =
        UnreadNotificationCountResponse(
            count = notificationRepository.countByUserIdAndReadAtIsNull(userId),
        )

    private fun normalizeLimit(limit: Int?): Int {
        val value = limit ?: DEFAULT_LIMIT
        if (value !in 1..MAX_LIMIT) {
            throw BusinessException(ErrorCode.INVALID_INPUT, detail = "limit must be between 1 and 50")
        }
        return value
    }

    companion object {
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 50
    }
}

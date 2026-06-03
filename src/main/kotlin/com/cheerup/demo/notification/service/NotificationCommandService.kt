package com.cheerup.demo.notification.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.notification.dto.NotificationResponse
import com.cheerup.demo.notification.dto.ReadAllNotificationsResponse
import com.cheerup.demo.notification.dto.toResponse
import com.cheerup.demo.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional(readOnly = true)
class NotificationCommandService(
    private val notificationRepository: NotificationRepository,
    private val clock: Clock,
) {

    @Transactional
    fun markAsRead(userId: Long, notificationId: Long): NotificationResponse {
        val notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            ?: throw BusinessException(
                ErrorCode.NOTIFICATION_NOT_FOUND,
                detail = "notificationId=$notificationId",
            )

        notification.markAsRead(clock.instant())
        return notification.toResponse()
    }

    @Transactional
    fun markAllAsRead(userId: Long): ReadAllNotificationsResponse =
        ReadAllNotificationsResponse(
            updatedCount = notificationRepository.markAllAsRead(
                userId = userId,
                readAt = clock.instant(),
            ),
        )
}

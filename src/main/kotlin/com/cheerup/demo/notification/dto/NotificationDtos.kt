package com.cheerup.demo.notification.dto

import com.cheerup.demo.notification.domain.Notification
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import java.time.Instant

data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val nextCursor: Long?,
)

data class NotificationResponse(
    val id: Long,
    val sourceType: NotificationSourceType,
    val sourceId: Long,
    val remindType: NotificationRemindType,
    val title: String,
    val message: String,
    val scheduledAt: Instant,
    val readAt: Instant?,
    val isRead: Boolean,
    val createdAt: Instant,
)

data class UnreadNotificationCountResponse(
    val count: Long,
)

data class ReadAllNotificationsResponse(
    val updatedCount: Int,
)

fun Notification.toResponse(): NotificationResponse =
    NotificationResponse(
        id = requireNotNull(id) { "Notification must be persisted" },
        sourceType = sourceType,
        sourceId = sourceId,
        remindType = remindType,
        title = title,
        message = message,
        scheduledAt = scheduledAt,
        readAt = readAt,
        isRead = readAt != null,
        createdAt = createdAt,
    )

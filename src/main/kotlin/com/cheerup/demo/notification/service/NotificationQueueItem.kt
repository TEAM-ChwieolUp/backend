package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType

data class NotificationQueueItem(
    val sourceType: NotificationSourceType,
    val sourceId: Long,
    val remindType: NotificationRemindType,
    val userId: Long,
) {
    fun toQueueValue(): String =
        "${sourceType.name}:$sourceId:${remindType.name}:$userId"

    companion object {
        fun parse(value: String): NotificationQueueItem? {
            val parts = value.split(":")
            if (parts.size != 4) return null

            val sourceType = runCatching { NotificationSourceType.valueOf(parts[0]) }.getOrNull()
                ?: return null
            val sourceId = parts[1].toLongOrNull() ?: return null
            val remindType = runCatching { NotificationRemindType.valueOf(parts[2]) }.getOrNull()
                ?: return null
            val userId = parts[3].toLongOrNull() ?: return null

            return NotificationQueueItem(
                sourceType = sourceType,
                sourceId = sourceId,
                remindType = remindType,
                userId = userId,
            )
        }
    }
}

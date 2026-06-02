package com.cheerup.demo.notification.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.notification.api.NotificationApi
import com.cheerup.demo.notification.dto.NotificationListResponse
import com.cheerup.demo.notification.dto.NotificationResponse
import com.cheerup.demo.notification.dto.ReadAllNotificationsResponse
import com.cheerup.demo.notification.dto.UnreadNotificationCountResponse
import com.cheerup.demo.notification.service.NotificationCommandService
import com.cheerup.demo.notification.service.NotificationQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationQueryService: NotificationQueryService,
    private val notificationCommandService: NotificationCommandService,
) : NotificationApi {

    @AssignUserId
    @GetMapping
    override fun getNotifications(
        userId: Long,
        @RequestParam(required = false) unreadOnly: Boolean?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) cursor: Long?,
    ): ApiResponse<NotificationListResponse> =
        ApiResponse.success(
            notificationQueryService.getNotifications(
                userId = userId,
                unreadOnly = unreadOnly ?: false,
                limit = limit,
                cursor = cursor,
            ),
        )

    @AssignUserId
    @GetMapping("/unread-count")
    override fun getUnreadCount(userId: Long): ApiResponse<UnreadNotificationCountResponse> =
        ApiResponse.success(notificationQueryService.getUnreadCount(userId))

    @AssignUserId
    @PatchMapping("/{id}/read")
    override fun markAsRead(
        userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<NotificationResponse> =
        ApiResponse.success(notificationCommandService.markAsRead(userId, id))

    @AssignUserId
    @PatchMapping("/read-all")
    override fun markAllAsRead(userId: Long): ApiResponse<ReadAllNotificationsResponse> =
        ApiResponse.success(notificationCommandService.markAllAsRead(userId))
}

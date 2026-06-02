package com.cheerup.demo.notification.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.notification.dto.NotificationListResponse
import com.cheerup.demo.notification.dto.NotificationResponse
import com.cheerup.demo.notification.dto.ReadAllNotificationsResponse
import com.cheerup.demo.notification.dto.UnreadNotificationCountResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Notification", description = "웹 사이트 내부 알림함 API")
interface NotificationApi {

    @Operation(summary = "알림 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
        ],
    )
    fun getNotifications(
        @Parameter(hidden = true) userId: Long,
        unreadOnly: Boolean?,
        limit: Int?,
        cursor: Long?,
    ): ApiResponse<NotificationListResponse>

    @Operation(summary = "읽지 않은 알림 수 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        ],
    )
    fun getUnreadCount(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<UnreadNotificationCountResponse>

    @Operation(summary = "알림 단건 읽음 처리")
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.NOTIFICATION_NOT_FOUND),
        ],
    )
    fun markAsRead(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "읽음 처리할 알림 ID.", example = "1001") id: Long,
    ): ApiResponse<NotificationResponse>

    @Operation(summary = "모든 알림 읽음 처리")
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        ],
    )
    fun markAllAsRead(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<ReadAllNotificationsResponse>
}

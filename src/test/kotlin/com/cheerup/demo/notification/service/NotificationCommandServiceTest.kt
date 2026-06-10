package com.cheerup.demo.notification.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.notification.domain.Notification
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import com.cheerup.demo.notification.repository.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NotificationCommandServiceTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var service: NotificationCommandService

    private val now = Instant.parse("2026-06-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk(relaxUnitFun = true)
        service = NotificationCommandService(notificationRepository, clock)
    }

    @Test
    fun `markAsRead stores readAt when notification is unread`() {
        val notification = fixtureNotification()
        every { notificationRepository.findByIdAndUserId(1001L, 99L) } returns notification

        val response = service.markAsRead(99L, 1001L)

        assertThat(notification.readAt).isEqualTo(now)
        assertThat(response.readAt).isEqualTo(now)
        assertThat(response.isRead).isTrue()
    }

    @Test
    fun `markAsRead throws NOTIFICATION_NOT_FOUND for other user notification`() {
        every { notificationRepository.findByIdAndUserId(1001L, 99L) } returns null

        assertThatThrownBy { service.markAsRead(99L, 1001L) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND)
            })
    }

    @Test
    fun `markAllAsRead returns updated count`() {
        every { notificationRepository.markAllAsRead(99L, now) } returns 3

        val response = service.markAllAsRead(99L)

        assertThat(response.updatedCount).isEqualTo(3)
        verify(exactly = 1) { notificationRepository.markAllAsRead(99L, now) }
    }

    private fun fixtureNotification(): Notification {
        val notification = Notification(
            userId = 99L,
            sourceType = NotificationSourceType.APPLICATION,
            sourceId = 101L,
            remindType = NotificationRemindType.D_DAY,
            title = "title",
            message = "message",
            scheduledAt = Instant.parse("2026-06-10T00:00:00Z"),
        )
        ReflectionTestUtils.setField(notification, "id", 1001L)
        ReflectionTestUtils.setField(notification, "createdAt", now)
        ReflectionTestUtils.setField(notification, "updatedAt", now)
        return notification
    }
}

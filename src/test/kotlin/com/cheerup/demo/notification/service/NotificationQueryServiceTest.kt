package com.cheerup.demo.notification.service

import com.cheerup.demo.global.exception.BusinessException
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
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class NotificationQueryServiceTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var service: NotificationQueryService

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk()
        service = NotificationQueryService(notificationRepository)
    }

    @Test
    fun `getNotifications returns id ordered page and next cursor`() {
        every {
            notificationRepository.findSlice(
                userId = 99L,
                unreadOnly = false,
                cursorId = null,
                pageable = PageRequest.of(0, 3),
            )
        } returns listOf(
            fixtureNotification(id = 1003L),
            fixtureNotification(id = 1002L),
            fixtureNotification(id = 1001L),
        )

        val response = service.getNotifications(
            userId = 99L,
            unreadOnly = false,
            limit = 2,
            cursor = null,
        )

        assertThat(response.notifications.map { it.id }).containsExactly(1003L, 1002L)
        assertThat(response.nextCursor).isEqualTo(1002L)
    }

    @Test
    fun `getNotifications returns null next cursor when there is no next page`() {
        every {
            notificationRepository.findSlice(
                userId = 99L,
                unreadOnly = false,
                cursorId = null,
                pageable = PageRequest.of(0, 21),
            )
        } returns listOf(fixtureNotification(id = 1001L))

        val response = service.getNotifications(
            userId = 99L,
            unreadOnly = false,
            limit = null,
            cursor = null,
        )

        assertThat(response.notifications.map { it.id }).containsExactly(1001L)
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `getNotifications uses unread flag and id cursor`() {
        every {
            notificationRepository.findSlice(
                userId = 99L,
                unreadOnly = true,
                cursorId = 1002L,
                pageable = PageRequest.of(0, 21),
            )
        } returns listOf(fixtureNotification(id = 1001L))

        service.getNotifications(
            userId = 99L,
            unreadOnly = true,
            limit = null,
            cursor = 1002L,
        )

        verify(exactly = 1) {
            notificationRepository.findSlice(
                userId = 99L,
                unreadOnly = true,
                cursorId = 1002L,
                pageable = PageRequest.of(0, 21),
            )
        }
    }

    @Test
    fun `getNotifications rejects invalid limit`() {
        assertThatThrownBy {
            service.getNotifications(
                userId = 99L,
                unreadOnly = false,
                limit = 51,
                cursor = null,
            )
        }.isInstanceOf(BusinessException::class.java)
    }

    private fun fixtureNotification(id: Long): Notification {
        val notification = Notification(
            userId = 99L,
            sourceType = NotificationSourceType.APPLICATION,
            sourceId = 101L,
            remindType = NotificationRemindType.D_DAY,
            title = "title",
            message = "message",
            scheduledAt = Instant.parse("2026-06-10T00:00:00Z"),
        )
        ReflectionTestUtils.setField(notification, "id", id)
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2026-06-01T00:00:00Z"))
        ReflectionTestUtils.setField(notification, "updatedAt", Instant.parse("2026-06-01T00:00:00Z"))
        return notification
    }
}

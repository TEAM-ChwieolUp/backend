package com.cheerup.demo.notification.controller

import com.cheerup.demo.global.jwt.JwtProvider
import com.cheerup.demo.notification.domain.Notification
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import com.cheerup.demo.notification.repository.NotificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @BeforeEach
    fun setUp() {
        notificationRepository.deleteAll()
    }

    @Test
    fun `getNotifications returns id ordered page unread filter and next cursor`() {
        val first = saveNotification(userId = 99L, sourceId = 101L, title = "first")
        val second = saveNotification(userId = 99L, sourceId = 102L, title = "second").also {
            it.markAsRead(Instant.parse("2026-06-02T00:00:00Z"))
            notificationRepository.save(it)
        }
        val third = saveNotification(userId = 99L, sourceId = 103L, title = "third")
        saveNotification(userId = 100L, sourceId = 101L, title = "other user")

        mockMvc.perform(
            get("/api/notifications")
                .queryParam("limit", "2")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications[0].id").value(third.id))
            .andExpect(jsonPath("$.data.notifications[1].id").value(second.id))
            .andExpect(jsonPath("$.data.nextCursor").value(second.id))

        mockMvc.perform(
            get("/api/notifications")
                .queryParam("unreadOnly", "true")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications[0].id").value(third.id))
            .andExpect(jsonPath("$.data.notifications[1].id").value(first.id))
            .andExpect(jsonPath("$.data.notifications.length()").value(2))
    }

    @Test
    fun `read endpoints update unread count for authenticated owner only`() {
        val first = saveNotification(userId = 99L, sourceId = 101L, title = "first")
        val second = saveNotification(userId = 99L, sourceId = 102L, title = "second")
        val otherUserNotification = saveNotification(userId = 100L, sourceId = 101L, title = "other user")

        mockMvc.perform(
            get("/api/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.count").value(2))

        mockMvc.perform(
            patch("/api/notifications/{id}/read", first.id)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(first.id))
            .andExpect(jsonPath("$.data.readAt", notNullValue()))

        mockMvc.perform(
            get("/api/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.count").value(1))

        mockMvc.perform(
            patch("/api/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.updatedCount").value(1))

        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(99L)).isZero()
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(100L)).isEqualTo(1)
        assertThat(notificationRepository.findByIdAndUserId(second.id!!, 99L)?.readAt).isNotNull()
        assertThat(notificationRepository.findByIdAndUserId(otherUserNotification.id!!, 100L)?.readAt).isNull()
    }

    @Test
    fun `markAsRead returns not found for another user's notification`() {
        val notification = saveNotification(userId = 100L, sourceId = 101L, title = "other user")

        mockMvc.perform(
            patch("/api/notifications/{id}/read", notification.id)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(99L)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
    }

    private fun saveNotification(
        userId: Long,
        sourceId: Long,
        title: String,
    ): Notification =
        notificationRepository.save(
            Notification(
                userId = userId,
                sourceType = NotificationSourceType.APPLICATION,
                sourceId = sourceId,
                remindType = NotificationRemindType.D_DAY,
                title = title,
                message = "$title message",
                scheduledAt = Instant.parse("2026-06-10T09:00:00Z"),
            ),
        )

    private fun bearerToken(userId: Long): String =
        "Bearer ${jwtProvider.generateAccessToken(userId)}"
}

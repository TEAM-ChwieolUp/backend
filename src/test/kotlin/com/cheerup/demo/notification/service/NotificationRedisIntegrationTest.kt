package com.cheerup.demo.notification.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import com.cheerup.demo.notification.repository.NotificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

@SpringBootTest(
    properties = [
        "app.notification.due-initial-delay-ms=3600000",
        "app.notification.due-poll-interval-ms=3600000",
    ],
)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class NotificationRedisIntegrationTest {

    @Autowired
    private lateinit var queue: RedisNotificationQueue

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var dueScheduler: NotificationDueScheduler

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @BeforeEach
    fun setUp() {
        redisTemplate.delete(RedisNotificationQueue.DUE_KEY)
        notificationRepository.deleteAll()
        applicationRepository.deleteAll()
    }

    @Test
    fun `redis queue stores updates and removes expected zset values`() {
        val originalDeadlineAt = Instant.parse("2099-06-10T09:00:00Z")
        val changedDeadlineAt = Instant.parse("2099-06-12T09:00:00Z")

        queue.enqueueApplicationDeadline(
            userId = 99L,
            applicationId = 101L,
            deadlineAt = originalDeadlineAt,
        )

        assertScore(
            value = "APPLICATION:101:D_MINUS_3:99",
            expectedTriggerAt = originalDeadlineAt.minus(Duration.ofDays(3)),
        )
        assertScore(
            value = "APPLICATION:101:D_MINUS_1:99",
            expectedTriggerAt = originalDeadlineAt.minus(Duration.ofDays(1)),
        )
        assertScore(
            value = "APPLICATION:101:D_DAY:99",
            expectedTriggerAt = originalDeadlineAt,
        )

        queue.updateApplicationDeadline(
            userId = 99L,
            applicationId = 101L,
            deadlineAt = changedDeadlineAt,
        )

        assertThat(score("APPLICATION:101:D_MINUS_3:99"))
            .isEqualTo(changedDeadlineAt.minus(Duration.ofDays(3)).toEpochMilli().toDouble())
        assertThat(score("APPLICATION:101:D_MINUS_1:99"))
            .isEqualTo(changedDeadlineAt.minus(Duration.ofDays(1)).toEpochMilli().toDouble())
        assertThat(score("APPLICATION:101:D_DAY:99"))
            .isEqualTo(changedDeadlineAt.toEpochMilli().toDouble())

        queue.removeByApplicationId(userId = 99L, applicationId = 101L)

        assertThat(score("APPLICATION:101:D_MINUS_3:99")).isNull()
        assertThat(score("APPLICATION:101:D_MINUS_1:99")).isNull()
        assertThat(score("APPLICATION:101:D_DAY:99")).isNull()
    }

    @Test
    fun `scheduler converts due redis item into notification row and removes queue value`() {
        val deadlineAt = Instant.parse("2099-06-10T09:00:00Z")
        val application = applicationRepository.save(
            Application(
                userId = 99L,
                stageId = 1L,
                companyName = "Toss",
                position = "Backend",
                deadlineAt = deadlineAt,
                priority = Priority.NORMAL,
            ),
        )
        val applicationId = requireNotNull(application.id)
        val queueValue = NotificationQueueItem(
            sourceType = NotificationSourceType.APPLICATION,
            sourceId = applicationId,
            remindType = NotificationRemindType.D_MINUS_1,
            userId = 99L,
        ).toQueueValue()

        redisTemplate.opsForZSet().add(
            RedisNotificationQueue.DUE_KEY,
            queueValue,
            Instant.now().minusSeconds(1).toEpochMilli().toDouble(),
        )

        dueScheduler.pollDueNotifications()

        assertThat(score(queueValue)).isNull()
        val notifications = notificationRepository.findAll()
        assertThat(notifications).hasSize(1)

        val notification = notifications.single()
        assertThat(notification.userId).isEqualTo(99L)
        assertThat(notification.sourceType).isEqualTo(NotificationSourceType.APPLICATION)
        assertThat(notification.sourceId).isEqualTo(applicationId)
        assertThat(notification.remindType).isEqualTo(NotificationRemindType.D_MINUS_1)
        assertThat(notification.scheduledAt).isEqualTo(deadlineAt)
        assertThat(notification.readAt).isNull()
    }

    private fun assertScore(value: String, expectedTriggerAt: Instant) {
        assertThat(score(value)).isEqualTo(expectedTriggerAt.toEpochMilli().toDouble())
    }

    private fun score(value: String): Double? =
        redisTemplate.opsForZSet().score(RedisNotificationQueue.DUE_KEY, value)

    private class RedisContainer : GenericContainer<RedisContainer>(
        DockerImageName.parse("redis:7.2-alpine"),
    )

    companion object {
        @Container
        @JvmStatic
        private val redis = RedisContainer().withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}

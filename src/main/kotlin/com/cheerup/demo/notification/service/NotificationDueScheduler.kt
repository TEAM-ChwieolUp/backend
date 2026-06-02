package com.cheerup.demo.notification.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class NotificationDueScheduler(
    private val dueQueue: NotificationDueQueue,
    private val dueService: NotificationDueService,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${app.notification.due-poll-interval-ms:60000}",
        initialDelayString = "\${app.notification.due-initial-delay-ms:60000}",
    )
    fun pollDueNotifications() {
        val dueValues = dueQueue.findDue(
            now = clock.instant(),
            limit = DEFAULT_BATCH_SIZE,
        )

        dueValues.forEach { value ->
            try {
                if (dueService.createNotification(value)) {
                    dueQueue.removeValue(value)
                }
            } catch (ex: RuntimeException) {
                log.warn("Failed to process notification queue value: {}", value, ex)
            }
        }
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 100L
    }
}

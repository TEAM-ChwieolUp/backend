package com.cheerup.demo.notification.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.notification.domain.Notification
import com.cheerup.demo.notification.domain.NotificationSourceType
import com.cheerup.demo.notification.repository.NotificationRepository
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NotificationDueService(
    private val notificationRepository: NotificationRepository,
    private val applicationRepository: ApplicationRepository,
    private val scheduleEventRepository: ScheduleEventRepository,
    private val messageFactory: NotificationMessageFactory,
) {

    @Transactional
    fun createNotification(queueValue: String): Boolean {
        val item = NotificationQueueItem.parse(queueValue) ?: return true

        val notification = when (item.sourceType) {
            NotificationSourceType.APPLICATION -> {
                val application = applicationRepository.findByIdAndUserId(item.sourceId, item.userId)
                    ?: return true
                val scheduledAt = application.deadlineAt ?: return true
                if (alreadyCreated(item, scheduledAt)) {
                    return true
                }
                val message = messageFactory.applicationDeadline(application, item.remindType)

                Notification(
                    userId = item.userId,
                    sourceType = item.sourceType,
                    sourceId = item.sourceId,
                    remindType = item.remindType,
                    title = message.title,
                    message = message.message,
                    scheduledAt = scheduledAt,
                )
            }

            NotificationSourceType.SCHEDULE_EVENT -> {
                val event = scheduleEventRepository.findByIdAndUserId(item.sourceId, item.userId)
                    ?: return true
                if (alreadyCreated(item, event.startAt)) {
                    return true
                }
                val message = messageFactory.scheduleEvent(event, item.remindType)

                Notification(
                    userId = item.userId,
                    sourceType = item.sourceType,
                    sourceId = item.sourceId,
                    remindType = item.remindType,
                    title = message.title,
                    message = message.message,
                    scheduledAt = event.startAt,
                )
            }
        }

        notificationRepository.save(notification)
        return true
    }

    private fun alreadyCreated(
        item: NotificationQueueItem,
        scheduledAt: Instant,
    ): Boolean =
        notificationRepository.existsByUserIdAndSourceTypeAndSourceIdAndRemindTypeAndScheduledAt(
            userId = item.userId,
            sourceType = item.sourceType,
            sourceId = item.sourceId,
            remindType = item.remindType,
            scheduledAt = scheduledAt,
        )
}

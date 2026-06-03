package com.cheerup.demo.notification.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.schedule.domain.ScheduleEvent
import org.springframework.stereotype.Component

@Component
class NotificationMessageFactory {

    fun applicationDeadline(
        application: Application,
        remindType: NotificationRemindType,
    ): NotificationMessage {
        val companyName = application.companyName
        return when (remindType) {
            NotificationRemindType.D_MINUS_3 -> NotificationMessage(
                title = "$companyName 채용 마감 D-3",
                message = "$companyName 채용 마감이 3일 남았습니다.",
            )

            NotificationRemindType.D_MINUS_1 -> NotificationMessage(
                title = "$companyName 채용 마감 D-1",
                message = "$companyName 채용 마감이 1일 남았습니다.",
            )

            NotificationRemindType.D_DAY -> NotificationMessage(
                title = "$companyName 채용 마감일",
                message = "오늘 $companyName 채용 마감일입니다.",
            )
        }
    }

    fun scheduleEvent(
        event: ScheduleEvent,
        remindType: NotificationRemindType,
    ): NotificationMessage =
        when (remindType) {
            NotificationRemindType.D_MINUS_3 -> NotificationMessage(
                title = "${event.title} D-3",
                message = "${event.title} 일정이 3일 남았습니다.",
            )

            NotificationRemindType.D_MINUS_1 -> NotificationMessage(
                title = "${event.title} D-1",
                message = "${event.title} 일정이 1일 남았습니다.",
            )

            NotificationRemindType.D_DAY -> NotificationMessage(
                title = event.title,
                message = "오늘 예정된 일정입니다.",
            )
        }
}

data class NotificationMessage(
    val title: String,
    val message: String,
)

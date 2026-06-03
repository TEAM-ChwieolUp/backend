package com.cheerup.demo.notification.service

import java.time.Instant

interface NotificationQueue {
    fun enqueueApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant)
    fun updateApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant)
    fun removeByApplicationId(userId: Long, applicationId: Long)

    fun enqueueScheduleEvent(userId: Long, eventId: Long, startAt: Instant)
    fun updateScheduleEvent(userId: Long, eventId: Long, startAt: Instant)
    fun removeByEventId(userId: Long, eventId: Long)
}

object NoOpNotificationQueue : NotificationQueue {
    override fun enqueueApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant) = Unit
    override fun updateApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant) = Unit
    override fun removeByApplicationId(userId: Long, applicationId: Long) = Unit
    override fun enqueueScheduleEvent(userId: Long, eventId: Long, startAt: Instant) = Unit
    override fun updateScheduleEvent(userId: Long, eventId: Long, startAt: Instant) = Unit
    override fun removeByEventId(userId: Long, eventId: Long) = Unit
}

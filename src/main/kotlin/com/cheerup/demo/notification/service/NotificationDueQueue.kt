package com.cheerup.demo.notification.service

import java.time.Instant

interface NotificationDueQueue {
    fun findDue(now: Instant, limit: Long): Set<String>
    fun removeValue(value: String)
}

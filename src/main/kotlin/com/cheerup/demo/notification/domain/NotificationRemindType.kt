package com.cheerup.demo.notification.domain

import java.time.Duration

enum class NotificationRemindType(
    val offset: Duration,
    val label: String,
) {
    D_MINUS_3(Duration.ofDays(3), "D-3"),
    D_MINUS_1(Duration.ofDays(1), "D-1"),
    D_DAY(Duration.ZERO, "D-Day"),
}

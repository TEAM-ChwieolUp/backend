package com.cheerup.demo.notification.service

import com.cheerup.demo.notification.domain.NotificationRemindType
import com.cheerup.demo.notification.domain.NotificationSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationQueueItemTest {

    @Test
    fun `toQueueValue and parse round trip`() {
        val item = NotificationQueueItem(
            sourceType = NotificationSourceType.APPLICATION,
            sourceId = 101L,
            remindType = NotificationRemindType.D_MINUS_1,
            userId = 99L,
        )

        val parsed = NotificationQueueItem.parse(item.toQueueValue())

        assertThat(parsed).isEqualTo(item)
    }

    @Test
    fun `parse returns null for invalid queue value`() {
        assertThat(NotificationQueueItem.parse("APPLICATION:bad:D_DAY:99")).isNull()
        assertThat(NotificationQueueItem.parse("APPLICATION:101:D_DAY")).isNull()
        assertThat(NotificationQueueItem.parse("UNKNOWN:101:D_DAY:99")).isNull()
    }
}

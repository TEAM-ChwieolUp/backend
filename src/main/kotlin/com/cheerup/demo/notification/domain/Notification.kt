package com.cheerup.demo.notification.domain

import com.cheerup.demo.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notifications_user_source_remind_scheduled",
            columnNames = ["user_id", "source_type", "source_id", "remind_type", "scheduled_at"],
        ),
    ],
    indexes = [
        Index(name = "idx_notifications_user_id_id", columnList = "user_id,id"),
        Index(name = "idx_notifications_user_id_read_at_id", columnList = "user_id,read_at,id"),
        Index(name = "idx_notifications_source", columnList = "source_type,source_id"),
    ],
)
class Notification(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    val sourceType: NotificationSourceType,

    @Column(name = "source_id", nullable = false)
    val sourceId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "remind_type", nullable = false, length = 32)
    val remindType: NotificationRemindType,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, length = 500)
    var message: String,

    @Column(name = "scheduled_at", nullable = false)
    val scheduledAt: Instant,

    @Column(name = "read_at")
    var readAt: Instant? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun markAsRead(readAt: Instant) {
        if (this.readAt == null) {
            this.readAt = readAt
        }
    }
}

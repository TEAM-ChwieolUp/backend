package com.cheerup.demo.schedule.domain

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
import java.time.Instant

@Entity
@Table(
    name = "schedule_events",
    indexes = [
        Index(name = "idx_schedule_events_user_id_start_at", columnList = "user_id,start_at"),
        Index(name = "idx_schedule_events_user_id_category_start_at", columnList = "user_id,category,start_at"),
        Index(name = "idx_schedule_events_application_id", columnList = "application_id"),
    ],
)
class ScheduleEvent(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "application_id")
    var applicationId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: ScheduleCategory,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(name = "start_at", nullable = false)
    var startAt: Instant,

    @Column(name = "end_at")
    var endAt: Instant? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun reschedule(startAt: Instant, endAt: Instant?) {
        this.startAt = startAt
        this.endAt = endAt
    }
}

package com.cheerup.demo.application.domain

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
    name = "applications",
    indexes = [
        Index(name = "idx_applications_user_id_stage_id", columnList = "user_id,stage_id"),
        Index(name = "idx_applications_user_id_deadline_at", columnList = "user_id,deadline_at"),
    ],
)
class Application(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "stage_id", nullable = false)
    var stageId: Long,

    @Column(name = "company_name", nullable = false, length = 100)
    var companyName: String,

    @Column(nullable = false, length = 100)
    var position: String,

    @Column(name = "applied_at")
    var appliedAt: Instant? = null,

    @Column(name = "deadline_at")
    var deadlineAt: Instant? = null,

    @Column(name = "no_response_days")
    var noResponseDays: Int? = 7,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var priority: Priority = Priority.NORMAL,

    @Column(columnDefinition = "TEXT")
    var memo: String? = null,

    @Column(name = "job_posting_url", length = 2048)
    var jobPostingUrl: String? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun changeStage(newStageId: Long) {
        stageId = newStageId
    }

    fun updateMemo(newMemo: String?) {
        memo = newMemo
    }

    fun setDeadline(newDeadlineAt: Instant?): Boolean {
        if (deadlineAt == newDeadlineAt) return false
        deadlineAt = newDeadlineAt
        return true
    }
}

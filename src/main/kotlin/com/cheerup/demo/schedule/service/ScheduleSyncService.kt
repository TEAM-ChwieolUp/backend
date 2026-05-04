package com.cheerup.demo.schedule.service

import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface ScheduleSyncService {
    fun syncApplicationDeadline(
        userId: Long,
        applicationId: Long,
        companyName: String,
        deadlineAt: Instant?,
    )

    fun deleteByApplicationId(userId: Long, applicationId: Long)
}

object NoOpScheduleSyncService : ScheduleSyncService {
    override fun syncApplicationDeadline(
        userId: Long,
        applicationId: Long,
        companyName: String,
        deadlineAt: Instant?,
    ) = Unit

    override fun deleteByApplicationId(userId: Long, applicationId: Long) = Unit
}

@Service
@Transactional
class DefaultScheduleSyncService(
    private val scheduleEventRepository: ScheduleEventRepository,
) : ScheduleSyncService {

    override fun syncApplicationDeadline(
        userId: Long,
        applicationId: Long,
        companyName: String,
        deadlineAt: Instant?,
    ) {
        val existing = scheduleEventRepository.findByUserIdAndApplicationIdAndCategory(
            userId = userId,
            applicationId = applicationId,
            category = ScheduleCategory.JOB_POSTING,
        )

        when {
            deadlineAt == null && existing == null -> Unit
            deadlineAt == null && existing != null -> scheduleEventRepository.delete(existing)
            deadlineAt != null && existing == null -> {
                scheduleEventRepository.save(
                    ScheduleEvent(
                        userId = userId,
                        applicationId = applicationId,
                        category = ScheduleCategory.JOB_POSTING,
                        title = jobPostingTitle(companyName),
                        startAt = deadlineAt,
                        endAt = null,
                    ),
                )
            }

            deadlineAt != null && existing != null -> {
                existing.title = jobPostingTitle(companyName)
                existing.reschedule(startAt = deadlineAt, endAt = null)
            }
        }
    }

    override fun deleteByApplicationId(userId: Long, applicationId: Long) {
        val events = scheduleEventRepository.findAllByUserIdAndApplicationId(userId, applicationId)
        if (events.isNotEmpty()) {
            scheduleEventRepository.deleteAll(events)
        }
    }

    private fun jobPostingTitle(companyName: String): String =
        "$companyName 채용 마감"
}

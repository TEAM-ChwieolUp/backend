package com.cheerup.demo.schedule.repository

import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.domain.ScheduleEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ScheduleEventRepository : JpaRepository<ScheduleEvent, Long> {

    fun findByIdAndUserId(id: Long, userId: Long): ScheduleEvent?

    fun findByUserIdAndApplicationIdAndCategory(
        userId: Long,
        applicationId: Long,
        category: ScheduleCategory,
    ): ScheduleEvent?

    fun findAllByUserIdAndApplicationId(
        userId: Long,
        applicationId: Long,
    ): List<ScheduleEvent>

    fun existsByUserIdAndApplicationIdAndCategory(
        userId: Long,
        applicationId: Long,
        category: ScheduleCategory,
    ): Boolean

    fun findAllByUserIdAndStartAtBetweenOrderByStartAtAscIdAsc(
        userId: Long,
        from: Instant,
        to: Instant,
    ): List<ScheduleEvent>

    fun findAllByUserIdAndCategoryInAndStartAtBetweenOrderByStartAtAscIdAsc(
        userId: Long,
        categories: Collection<ScheduleCategory>,
        from: Instant,
        to: Instant,
    ): List<ScheduleEvent>
}

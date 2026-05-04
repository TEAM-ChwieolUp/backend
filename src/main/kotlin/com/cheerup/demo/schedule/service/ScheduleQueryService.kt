package com.cheerup.demo.schedule.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.schedule.domain.ScheduleCategory
import com.cheerup.demo.schedule.dto.CalendarResponse
import com.cheerup.demo.schedule.dto.toCalendarEventResponse
import com.cheerup.demo.schedule.repository.ScheduleEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ScheduleQueryService(
    private val scheduleEventRepository: ScheduleEventRepository,
) {

    fun getCalendar(
        userId: Long,
        from: String?,
        to: String?,
        category: String?,
    ): CalendarResponse {
        val fromInstant = parseRequiredInstant("from", from)
        val toInstant = parseRequiredInstant("to", to)
        validateRange(fromInstant, toInstant)

        val categories = parseCategories(category)
        val events = if (categories.isEmpty()) {
            scheduleEventRepository.findAllByUserIdAndStartAtBetweenOrderByStartAtAscIdAsc(
                userId = userId,
                from = fromInstant,
                to = toInstant,
            )
        } else {
            scheduleEventRepository.findAllByUserIdAndCategoryInAndStartAtBetweenOrderByStartAtAscIdAsc(
                userId = userId,
                categories = categories,
                from = fromInstant,
                to = toInstant,
            )
        }

        return CalendarResponse(events.map { it.toCalendarEventResponse() })
    }

    private fun parseRequiredInstant(name: String, rawValue: String?): Instant {
        if (rawValue.isNullOrBlank()) {
            invalidInput("$name is required")
        }

        return runCatching { Instant.parse(rawValue) }
            .getOrElse { invalidInput("$name must be ISO-8601 UTC instant") }
    }

    private fun validateRange(from: Instant, to: Instant) {
        if (!from.isBefore(to)) {
            invalidInput("from must be before to")
        }

        val range = Duration.between(from, to)
        if (range > MAX_CALENDAR_RANGE) {
            invalidInput("calendar range must be 100 days or less")
        }
    }

    private fun parseCategories(categoryCsv: String?): Set<ScheduleCategory> {
        if (categoryCsv.isNullOrBlank()) {
            return emptySet()
        }

        val tokens = categoryCsv.split(",").map { it.trim() }
        if (tokens.any { it.isBlank() }) {
            invalidInput("category must be comma-separated ScheduleCategory values")
        }

        return tokens.mapTo(linkedSetOf()) { token ->
            runCatching { ScheduleCategory.valueOf(token) }
                .getOrElse { invalidInput("unsupported category=$token") }
        }
    }

    private fun invalidInput(detail: String): Nothing =
        throw BusinessException(ErrorCode.INVALID_INPUT, detail = detail)

    private companion object {
        val MAX_CALENDAR_RANGE: Duration = Duration.ofDays(100)
    }
}

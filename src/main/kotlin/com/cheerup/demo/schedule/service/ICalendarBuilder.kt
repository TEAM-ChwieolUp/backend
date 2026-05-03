package com.cheerup.demo.schedule.service

import com.cheerup.demo.schedule.domain.ScheduleEvent
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class ICalendarBuilder {

    fun build(event: ScheduleEvent): String {
        val eventId = requireNotNull(event.id) { "ScheduleEvent must be persisted" }

        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//cheerup//ko//")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:cheerup-event-$eventId@cheerup.app")
            appendLine("DTSTAMP:${formatInstant(Instant.now())}")
            appendLine("DTSTART:${formatInstant(event.startAt)}")
            event.endAt?.let { appendLine("DTEND:${formatInstant(it)}") }
            appendLine("SUMMARY:${escapeText(event.title)}")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }
    }

    private fun formatInstant(instant: Instant): String =
        FORMATTER.format(instant)

    private fun escapeText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
    }
}

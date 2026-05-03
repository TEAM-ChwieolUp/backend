package com.cheerup.demo.schedule.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.schedule.api.ScheduleApi
import com.cheerup.demo.schedule.dto.CalendarResponse
import com.cheerup.demo.schedule.dto.CreateScheduleEventRequest
import com.cheerup.demo.schedule.dto.ScheduleEventResponse
import com.cheerup.demo.schedule.dto.UpdateScheduleEventRequest
import com.cheerup.demo.schedule.service.ScheduleEventCommandService
import com.cheerup.demo.schedule.service.ScheduleQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/schedule")
class ScheduleController(
    private val scheduleQueryService: ScheduleQueryService,
    private val scheduleEventCommandService: ScheduleEventCommandService,
) : ScheduleApi {

    @AssignUserId
    @GetMapping("/calendar")
    override fun getCalendar(
        userId: Long,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) category: String?,
    ): ApiResponse<CalendarResponse> =
        ApiResponse.success(scheduleQueryService.getCalendar(userId, from, to, category))

    @AssignUserId
    @PostMapping("/events")
    override fun createEvent(
        userId: Long,
        @Valid @RequestBody request: CreateScheduleEventRequest,
    ): ResponseEntity<ApiResponse<ScheduleEventResponse>> {
        val created = scheduleEventCommandService.create(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created))
    }

    @AssignUserId
    @PatchMapping("/events/{id}")
    override fun updateEvent(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateScheduleEventRequest,
    ): ApiResponse<ScheduleEventResponse> =
        ApiResponse.success(scheduleEventCommandService.update(userId, id, request))

    @AssignUserId
    @DeleteMapping("/events/{id}")
    override fun deleteEvent(
        userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        scheduleEventCommandService.delete(userId, id)
        return ResponseEntity.noContent().build()
    }

    @AssignUserId
    @GetMapping("/events/{id}/export")
    override fun exportEvent(
        userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<String> {
        val content = scheduleEventCommandService.export(userId, id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cheerup-event-$id.ics\"")
            .body(content)
    }
}

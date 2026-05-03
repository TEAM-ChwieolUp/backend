package com.cheerup.demo.schedule.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.schedule.dto.CalendarResponse
import com.cheerup.demo.schedule.dto.CreateScheduleEventRequest
import com.cheerup.demo.schedule.dto.ScheduleEventResponse
import com.cheerup.demo.schedule.dto.UpdateScheduleEventRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Schedule", description = "채용공고, 채용 전형, 개인 일정을 한 달력에서 다루는 API")
interface ScheduleApi {

    @Operation(
        summary = "달력 조회",
        description = "프론트 달력 화면에서 가장 먼저 호출하는 API입니다. " +
            "현재 보이는 달력 범위를 UTC ISO-8601 instant로 from/to에 넣어 요청하면, " +
            "채용공고(JOB_POSTING), 채용 전형(APPLICATION_PROCESS), 개인 일정(PERSONAL)을 startAt 오름차순으로 반환합니다. " +
            "category를 생략하면 전체 레이어를 조회하고, 필요한 레이어만 보려면 쉼표로 여러 값을 전달합니다. " +
            "applicationId가 있는 일정은 칸반 카드 상세로 이동할 때 사용할 수 있습니다. 조회 범위는 최대 100일입니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
        ],
    )
    fun getCalendar(
        @Parameter(hidden = true) userId: Long,
        @Parameter(
            description = "조회 구간 시작 시각(포함). ISO-8601 UTC instant",
            required = true,
            example = "2026-05-01T00:00:00Z",
        ) from: String?,
        @Parameter(
            description = "조회 구간 종료 시각(포함). ISO-8601 UTC instant",
            required = true,
            example = "2026-05-31T23:59:59Z",
        ) to: String?,
        @Parameter(
            description = "쉼표로 구분한 카테고리 목록. 미지정 시 전체 조회. " +
                "예: JOB_POSTING,APPLICATION_PROCESS (값: JOB_POSTING/APPLICATION_PROCESS/PERSONAL)",
            example = "JOB_POSTING,APPLICATION_PROCESS",
        ) category: String?,
    ): ApiResponse<CalendarResponse>

    @Operation(
        summary = "일정 생성",
        description = "달력 화면이나 메일 분석 결과 수락 플로우에서 일정을 생성합니다. " +
            "APPLICATION_PROCESS는 특정 채용 카드에 연결되는 면접/코딩테스트/발표 일정이고 applicationId가 필수입니다. " +
            "PERSONAL은 개인 일정이며 applicationId를 보내면 안 됩니다. " +
            "JOB_POSTING도 생성할 수 있지만 같은 applicationId에 이미 JOB_POSTING이 있으면 409를 반환하므로, " +
            "일반 채용 마감일은 보통 Application 생성/수정 API의 deadlineAt 동기화로 만들어집니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.APPLICATION_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.SCHEDULE_DUPLICATE_JOB_POSTING),
        ],
    )
    fun createEvent(
        @Parameter(hidden = true) userId: Long,
        request: CreateScheduleEventRequest,
    ): ResponseEntity<ApiResponse<ScheduleEventResponse>>

    @Operation(
        summary = "일정 수정",
        description = "달력 이벤트 드래그/리사이즈 또는 상세 모달 저장 시 호출합니다. " +
            "title, startAt, endAt만 부분 수정할 수 있고 null 필드는 변경하지 않습니다. " +
            "category/applicationId는 변경 불가라 요청에 포함돼도 무시합니다. " +
            "JOB_POSTING을 직접 수정할 수는 있지만, 연결된 Application의 deadlineAt이 나중에 바뀌면 다시 동기화 값으로 덮어써질 수 있습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.SCHEDULE_NOT_FOUND),
        ],
    )
    fun updateEvent(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 일정 ID", example = "501") id: Long,
        request: UpdateScheduleEventRequest,
    ): ApiResponse<ScheduleEventResponse>

    @Operation(
        summary = "일정 삭제",
        description = "달력 이벤트 삭제 시 호출합니다. 본인 소유가 아닌 일정은 404로 응답합니다. " +
            "Application deadlineAt과 연결된 JOB_POSTING 일정은 직접 삭제할 수 없습니다. " +
            "이 경우 프론트는 사용자에게 칸반 카드에서 마감일을 먼저 비우도록 안내하면 됩니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.SCHEDULE_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.SCHEDULE_JOB_POSTING_LOCKED),
        ],
    )
    fun deleteEvent(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 일정 ID", example = "502") id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "iCalendar export",
        description = "일정 상세에서 외부 캘린더로 내보내기 버튼을 눌렀을 때 호출합니다. " +
            "단일 일정만 .ics 파일로 내려주며 (Content-Type: text/calendar; charset=UTF-8, " +
            "파일명: cheerup-event-{id}.ics), Google Calendar/Outlook에 사용자가 직접 import하는 일회성 다운로드입니다. " +
            "이후 CheerUp 일정이 수정/삭제되어도 이미 import된 외부 캘린더 일정은 자동 동기화되지 않습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.SCHEDULE_NOT_FOUND),
        ],
    )
    fun exportEvent(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "내보낼 일정 ID", example = "502") id: Long,
    ): ResponseEntity<String>
}

package com.cheerup.demo.schedule.api

import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.global.response.ErrorResponse
import com.cheerup.demo.schedule.dto.CalendarResponse
import com.cheerup.demo.schedule.dto.CreateScheduleEventRequest
import com.cheerup.demo.schedule.dto.ScheduleEventResponse
import com.cheerup.demo.schedule.dto.UpdateScheduleEventRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
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
        parameters = [
            Parameter(
                name = "from",
                description = "조회 구간 시작 시각(포함). ISO-8601 UTC instant",
                required = true,
                `in` = ParameterIn.QUERY,
                example = "2026-05-01T00:00:00Z",
            ),
            Parameter(
                name = "to",
                description = "조회 구간 종료 시각(포함). ISO-8601 UTC instant",
                required = true,
                `in` = ParameterIn.QUERY,
                example = "2026-05-31T23:59:59Z",
            ),
            Parameter(
                name = "category",
                description = "쉼표로 구분한 카테고리 목록. 미지정 시 전체 조회. 예: JOB_POSTING,APPLICATION_PROCESS",
                `in` = ParameterIn.QUERY,
                schema = Schema(allowableValues = ["JOB_POSTING", "APPLICATION_PROCESS", "PERSONAL"]),
                example = "JOB_POSTING,APPLICATION_PROCESS",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "조회 성공. data.events는 startAt ASC, id ASC 순서입니다.",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = CalendarResponse::class),
                examples = [
                    ExampleObject(
                        name = "calendar",
                        value = """{"data":{"events":[{"id":501,"applicationId":101,"category":"JOB_POSTING","title":"토스 채용 마감","startAt":"2026-05-10T14:00:00Z","endAt":null},{"id":502,"applicationId":101,"category":"APPLICATION_PROCESS","title":"1차 면접","startAt":"2026-05-15T07:00:00Z","endAt":"2026-05-15T08:00:00Z"},{"id":600,"applicationId":null,"category":"PERSONAL","title":"스터디","startAt":"2026-05-12T10:00:00Z","endAt":null}]},"meta":{}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "조회 조건 검증 실패. from/to 누락, ISO-8601 형식 오류, from >= to, 100일 초과, 잘못된 category 값",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun getCalendar(
        userId: Long,
        from: String?,
        to: String?,
        category: String?,
    ): ApiResponse<CalendarResponse>

    @Operation(
        summary = "일정 생성",
        description = "달력 화면이나 메일 분석 결과 수락 플로우에서 일정을 생성합니다. " +
            "APPLICATION_PROCESS는 특정 채용 카드에 연결되는 면접/코딩테스트/발표 일정이고 applicationId가 필수입니다. " +
            "PERSONAL은 개인 일정이며 applicationId를 보내면 안 됩니다. " +
            "JOB_POSTING도 생성할 수 있지만 같은 applicationId에 이미 JOB_POSTING이 있으면 409를 반환하므로, " +
            "일반 채용 마감일은 보통 Application 생성/수정 API의 deadlineAt 동기화로 만들어집니다.",
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateScheduleEventRequest::class),
                    examples = [
                        ExampleObject(
                            name = "applicationProcess",
                            summary = "채용 전형 일정",
                            value = """{"category":"APPLICATION_PROCESS","applicationId":101,"title":"1차 면접","startAt":"2026-05-15T07:00:00Z","endAt":"2026-05-15T08:00:00Z"}""",
                        ),
                        ExampleObject(
                            name = "personal",
                            summary = "개인 일정",
                            value = """{"category":"PERSONAL","applicationId":null,"title":"스터디","startAt":"2026-05-12T10:00:00Z","endAt":null}""",
                        ),
                        ExampleObject(
                            name = "jobPosting",
                            summary = "채용공고 일정 직접 등록",
                            value = """{"category":"JOB_POSTING","applicationId":101,"title":"토스 채용 마감","startAt":"2026-05-10T14:00:00Z","endAt":null}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "201",
        description = "생성 성공. 생성된 단일 일정이 data에 담깁니다.",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ScheduleEventResponse::class),
                examples = [
                    ExampleObject(
                        name = "created",
                        value = """{"data":{"id":502,"applicationId":101,"category":"APPLICATION_PROCESS","title":"1차 면접","startAt":"2026-05-15T07:00:00Z","endAt":"2026-05-15T08:00:00Z"},"meta":{}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패. PERSONAL에 applicationId가 있거나, APPLICATION_PROCESS/JOB_POSTING에 applicationId가 없거나, title/startAt/endAt 검증 실패",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "연결된 Application을 찾을 수 없거나 본인 소유가 아님 (APPLICATION_NOT_FOUND)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "같은 Application의 JOB_POSTING 일정 중복 (SCHEDULE_DUPLICATE_JOB_POSTING)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun createEvent(
        userId: Long,
        request: CreateScheduleEventRequest,
    ): ResponseEntity<ApiResponse<ScheduleEventResponse>>

    @Operation(
        summary = "일정 수정",
        description = "달력 이벤트 드래그/리사이즈 또는 상세 모달 저장 시 호출합니다. " +
            "title, startAt, endAt만 부분 수정할 수 있고 null 필드는 변경하지 않습니다. " +
            "category/applicationId는 변경 불가라 요청에 포함돼도 무시합니다. " +
            "JOB_POSTING을 직접 수정할 수는 있지만, 연결된 Application의 deadlineAt이 나중에 바뀌면 다시 동기화 값으로 덮어써질 수 있습니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "수정할 일정 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "501",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UpdateScheduleEventRequest::class),
                    examples = [
                        ExampleObject(
                            name = "rescheduleInterview",
                            summary = "일정 재조정",
                            value = """{"title":"1차 면접 (재일정)","startAt":"2026-05-16T07:00:00Z","endAt":"2026-05-16T08:00:00Z"}""",
                        ),
                        ExampleObject(
                            name = "renameOnly",
                            summary = "제목만 수정",
                            value = """{"title":"최종 면접"}""",
                        ),
                        ExampleObject(
                            name = "ignoreImmutableFields",
                            summary = "category/applicationId는 포함돼도 무시됨",
                            value = """{"category":"PERSONAL","applicationId":null,"title":"1차 면접"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "수정 성공. 수정된 단일 일정이 data에 담깁니다.",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ScheduleEventResponse::class),
                examples = [
                    ExampleObject(
                        name = "updated",
                        value = """{"data":{"id":502,"applicationId":101,"category":"APPLICATION_PROCESS","title":"1차 면접 (재일정)","startAt":"2026-05-16T07:00:00Z","endAt":"2026-05-16T08:00:00Z"},"meta":{}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패 또는 endAt < startAt",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "일정을 찾을 수 없거나 본인 소유가 아님 (SCHEDULE_NOT_FOUND)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun updateEvent(
        userId: Long,
        id: Long,
        request: UpdateScheduleEventRequest,
    ): ApiResponse<ScheduleEventResponse>

    @Operation(
        summary = "일정 삭제",
        description = "달력 이벤트 삭제 시 호출합니다. 본인 소유가 아닌 일정은 404로 응답합니다. " +
            "Application deadlineAt과 연결된 JOB_POSTING 일정은 직접 삭제할 수 없습니다. " +
            "이 경우 프론트는 사용자에게 칸반 카드에서 마감일을 먼저 비우도록 안내하면 됩니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "삭제할 일정 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "502",
            ),
        ],
    )
    @SwaggerApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)")
    @SwaggerApiResponse(
        responseCode = "404",
        description = "일정을 찾을 수 없거나 본인 소유가 아님 (SCHEDULE_NOT_FOUND)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "Application deadline과 연결된 JOB_POSTING 일정은 직접 삭제 불가 (SCHEDULE_JOB_POSTING_LOCKED)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun deleteEvent(userId: Long, id: Long): ResponseEntity<Void>

    @Operation(
        summary = "iCalendar export",
        description = "일정 상세에서 외부 캘린더로 내보내기 버튼을 눌렀을 때 호출합니다. " +
            "단일 일정만 .ics 파일로 내려주며, Google Calendar/Outlook에 사용자가 직접 import하는 일회성 다운로드입니다. " +
            "이후 CheerUp 일정이 수정/삭제되어도 이미 import된 외부 캘린더 일정은 자동 동기화되지 않습니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "내보낼 일정 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "502",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = ".ics 생성 성공. Content-Type은 text/calendar; charset=UTF-8, 파일명은 cheerup-event-{id}.ics",
        content = [
            Content(
                mediaType = "text/calendar",
                examples = [
                    ExampleObject(
                        name = "ics",
                        value = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//cheerup//ko//\nBEGIN:VEVENT\nUID:cheerup-event-502@cheerup.app\nDTSTAMP:20260501T030000Z\nDTSTART:20260515T070000Z\nDTEND:20260515T080000Z\nSUMMARY:1차 면접\nEND:VEVENT\nEND:VCALENDAR\n",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "일정을 찾을 수 없거나 본인 소유가 아님 (SCHEDULE_NOT_FOUND)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun exportEvent(userId: Long, id: Long): ResponseEntity<String>
}

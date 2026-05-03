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

@Tag(
    name = "Schedule",
    description = """
        취업 이중 달력 API. 3가지 카테고리를 단일 달력 UI에 통합 표시합니다.

        **카테고리 분기**
        - `JOB_POSTING` (채용공고 일정): 마감일/접수 시작/설명회. 보통 칸반 카드의 `deadlineAt` 동기화로 자동 생성됩니다. `applicationId` 필수.
        - `APPLICATION_PROCESS` (채용 전형): 면접/코딩테스트/발표. 사용자 또는 메일 분석으로 등록. `applicationId` 필수.
        - `PERSONAL` (개인 일정): 스터디/공부 등. `applicationId`는 null이어야 함.

        **시간**: 모든 시각은 UTC ISO-8601 instant. 타임존 변환은 프론트가 담당하세요.
    """,
)
interface ScheduleApi {

    @Operation(
        summary = "달력 조회",
        description = """
            달력 화면 진입 시 가장 먼저 호출하는 API입니다. 보이는 화면 범위(월/주)에 맞춰 반복 호출하세요.

            **요청 파라미터**
            - `from`/`to`: 조회 구간(둘 다 포함). 화면 범위가 5월이면 `2026-05-01T00:00:00Z` ~ `2026-05-31T23:59:59Z` 식으로 보냅니다.
            - 조회 범위는 **최대 100일**입니다. 그 이상은 `INVALID_INPUT`. 분할 호출하세요.
            - `category`: 쉼표로 구분한 카테고리 필터. 미지정 시 전체. 예: `JOB_POSTING,APPLICATION_PROCESS`로 보내면 PERSONAL이 빠집니다 (필터 칩 토글 UX에 활용).

            **응답 구조**
            - `data.events[]`: `startAt` 오름차순(동률 시 `id` 오름차순).
            - 각 이벤트의 `applicationId`가 있으면 칸반 카드 상세로 라우팅 가능 (`/applications/{applicationId}`).
            - `endAt`이 null이면 종료 시각 미정(보통 마감일 같은 단일 시점) → UI에서는 점/마커로 표시 권장.

            **호출 빈도 팁**: 사용자가 다음 달로 페이지를 넘기거나 카드/일정을 추가/수정한 직후에만 다시 호출하세요. 매 입력마다 호출하지 마세요.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. `/api/auth/refresh`로 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.INVALID_INPUT,
                description = "from/to 누락, ISO-8601 형식 오류, `from >= to`, 조회 범위 100일 초과, " +
                    "`category` 값에 enum 외 문자열 포함 등. 프론트는 화면 범위 계산 로직을 확인하세요.",
            ),
        ],
    )
    fun getCalendar(
        @Parameter(hidden = true) userId: Long,
        @Parameter(
            description = "조회 구간 시작 시각(포함). UTC ISO-8601 instant.",
            required = true,
            example = "2026-05-01T00:00:00Z",
        ) from: String?,
        @Parameter(
            description = "조회 구간 종료 시각(포함). UTC ISO-8601 instant. " +
                "`from`과의 간격은 최대 100일.",
            required = true,
            example = "2026-05-31T23:59:59Z",
        ) to: String?,
        @Parameter(
            description = "쉼표로 구분한 카테고리 필터. 미지정/빈 문자열이면 전체. " +
                "허용 값: `JOB_POSTING`, `APPLICATION_PROCESS`, `PERSONAL`.",
            example = "JOB_POSTING,APPLICATION_PROCESS",
        ) category: String?,
    ): ApiResponse<CalendarResponse>

    @Operation(
        summary = "일정 생성",
        description = """
            달력 빈 칸 클릭 → 일정 생성 모달, 또는 메일 AI 제안 수락 플로우에서 호출합니다.

            **카테고리별 요청 규칙**
            - `APPLICATION_PROCESS` (면접/코테/발표): `applicationId` **필수**. 칸반 카드와 연결됩니다.
            - `PERSONAL` (개인 일정): `applicationId`는 **null이어야** 합니다. 보내면 `INVALID_INPUT`.
            - `JOB_POSTING`: `applicationId` 필수. 같은 카드에 이미 `JOB_POSTING`이 있으면 409로 거부됩니다.
              일반적인 채용 마감일은 본 API가 아닌 **칸반 카드의 `deadlineAt` 동기화로 자동 생성**되므로,
              본 API로 직접 만들 일은 드뭅니다 (접수 시작/설명회 같은 추가 일정 등록 시에만).

            **공통**
            - `title`: 1~200자.
            - `startAt`/`endAt`: UTC ISO-8601 instant. `endAt`은 null 가능. 값이 있으면 `endAt >= startAt`.

            **응답**
            - HTTP `201 Created`, `data`에 생성된 단일 `ScheduleEventResponse`.
            - 달력 캐시에 받은 일정을 `startAt` 위치에 삽입하면 되며, 전체 재조회는 불필요합니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. `/api/auth/refresh`로 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.INVALID_INPUT,
                description = "title 검증 실패, `endAt < startAt`, " +
                    "PERSONAL인데 applicationId가 있거나 JOB_POSTING/APPLICATION_PROCESS인데 applicationId 누락 등. " +
                    "프론트는 폼에서 카테고리 분기를 미리 검증해 사용자 혼란을 줄이세요.",
            ),
            SwaggerErrorResponse(
                ErrorCode.APPLICATION_NOT_FOUND,
                description = "`applicationId`로 보낸 카드가 없거나 본인 소유가 아닙니다. " +
                    "카드 목록을 다시 불러와 picker를 갱신하세요.",
            ),
            SwaggerErrorResponse(
                ErrorCode.SCHEDULE_DUPLICATE_JOB_POSTING,
                description = "이미 같은 카드에 JOB_POSTING 일정이 있습니다. " +
                    "프론트는 \"이 카드에는 이미 채용공고 일정이 있어요. 카드의 마감일을 수정해 주세요\" 안내.",
            ),
        ],
    )
    fun createEvent(
        @Parameter(hidden = true) userId: Long,
        request: CreateScheduleEventRequest,
    ): ResponseEntity<ApiResponse<ScheduleEventResponse>>

    @Operation(
        summary = "일정 수정",
        description = """
            달력에서 일정 드래그(시간 이동)/리사이즈(길이 조절), 또는 일정 상세 모달 저장 시 호출합니다.

            **부분 수정 규칙**
            - 모든 필드 옵셔널. `null` = 변경 없음. 빈 본문(`{}`)은 변경 없이 200.
            - **`category`/`applicationId`는 변경 불가** — 요청에 포함돼도 **무시**됩니다 (에러는 안 남).
              카테고리/연결 카드를 바꾸고 싶으면 삭제 후 재생성하세요.

            **수정 가능 필드**: `title`, `startAt`, `endAt`.
            **검증**: `endAt`을 함께 보낼 때 `endAt >= startAt`이어야 합니다.

            **JOB_POSTING 주의**
            - 직접 수정은 가능하지만, 연결된 카드의 `deadlineAt`이 다시 바뀌면 본 일정이 동기화 값으로 덮어써질 수 있습니다.
              마감일 자체를 옮기려면 본 API가 아니라 카드의 `deadlineAt`을 수정하는 게 정석입니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. `/api/auth/refresh`로 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.INVALID_INPUT,
                description = "title 길이 초과(200자), `endAt < startAt` 등.",
            ),
            SwaggerErrorResponse(
                ErrorCode.SCHEDULE_NOT_FOUND,
                description = "일정이 없거나 본인 소유가 아닙니다. 달력을 다시 불러와 동기화.",
            ),
        ],
    )
    fun updateEvent(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 일정 ID.", example = "501") id: Long,
        request: UpdateScheduleEventRequest,
    ): ApiResponse<ScheduleEventResponse>

    @Operation(
        summary = "일정 삭제",
        description = """
            달력 일정 삭제 시 호출합니다.

            **JOB_POSTING 삭제 불가**
            - 카드의 `deadlineAt`과 연결된 `JOB_POSTING`은 직접 삭제 불가 (`SCHEDULE_JOB_POSTING_LOCKED`).
            - 프론트는 \"이 일정은 카드의 마감일과 연결돼 있어요. 칸반에서 마감일을 비운 뒤 다시 시도해 주세요\" 안내 + 해당 카드로 이동 버튼을 함께 제공하면 좋습니다.
            - 카드의 `deadlineAt`을 null로 PATCH하면 본 일정도 함께 자동 삭제됩니다.

            **응답**: HTTP `204 No Content`, 본문 없음. 달력 캐시에서 해당 일정 제거.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. `/api/auth/refresh`로 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.SCHEDULE_NOT_FOUND,
                description = "이미 삭제된 일정이거나 본인 소유가 아닙니다. 달력을 다시 불러와 동기화.",
            ),
            SwaggerErrorResponse(
                ErrorCode.SCHEDULE_JOB_POSTING_LOCKED,
                description = "카드의 `deadlineAt`과 연결된 JOB_POSTING 일정은 직접 삭제할 수 없습니다. " +
                    "프론트는 카드의 마감일을 비우도록 사용자에게 안내하세요.",
            ),
        ],
    )
    fun deleteEvent(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 일정 ID.", example = "502") id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "iCalendar(.ics) 다운로드",
        description = """
            일정 상세 모달의 "외부 캘린더로 내보내기" 버튼에서 호출합니다. 단일 일정만 `.ics` 파일로 다운로드합니다.

            **응답 헤더**
            - `Content-Type: text/calendar; charset=UTF-8`
            - `Content-Disposition: attachment; filename="cheerup-event-{id}.ics"`

            **호출 방법 (프론트 예시)**
            - 단순히 새 탭으로 열어도 OK: `window.open('/api/schedule/events/502/export')` (단, JWT를 쿠키로 보내야 인증됨).
            - 또는 fetch로 받아서 Blob → `URL.createObjectURL` → `<a download>` 트리거.

            **일회성 다운로드 모델**
            - 사용자는 받은 .ics를 Google Calendar / Outlook에 직접 import 합니다.
            - **양방향 동기화 아님** — 이후 CheerUp에서 일정을 수정/삭제해도 외부에 import된 일정은 자동 갱신되지 않습니다.
              사용자에게 "한번 더 export해 다시 import 해야 합니다" 안내가 필요할 수 있습니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. `/api/auth/refresh`로 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.SCHEDULE_NOT_FOUND,
                description = "일정이 없거나 본인 소유가 아닙니다. 달력을 다시 불러와 동기화.",
            ),
        ],
    )
    fun exportEvent(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "내보낼 일정 ID.", example = "502") id: Long,
    ): ResponseEntity<String>
}

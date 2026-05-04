package com.cheerup.demo.application.api

import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.dto.ApplicationCard
import com.cheerup.demo.application.dto.ApplicationResponse
import com.cheerup.demo.application.dto.BoardResponse
import com.cheerup.demo.application.dto.CreateApplicationRequest
import com.cheerup.demo.application.dto.UpdateApplicationRequest
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(
    name = "Applications",
    description = "채용 칸반 보드의 지원 카드(Application)를 다루는 API. " +
        "보드 화면 진입, 카드 생성/수정/삭제, 카드별 회고 생성 라우팅을 담당합니다.",
)
interface ApplicationApi {

    @Operation(
        summary = "칸반 보드 조회",
        description = """
            칸반 보드 화면 진입 시 가장 먼저 호출하는 API입니다.

            **응답 구조**
            - `data.stages[]`: Stage(컬럼) 목록을 `displayOrder` 오름차순으로 반환합니다.
            - `data.stages[].applications[]`: 각 Stage에 속한 카드 요약 목록입니다 (간단 카드 형태).
            - 카드 상세 필드(`memo`, `noResponseDays` 등)는 응답에 포함되지 않으며,
              상세 모달에서 필요하면 PATCH 응답으로 받은 값을 사용하거나 별도 카드 단건 조회를 호출하세요.

            **필터**
            - 모든 필터는 선택입니다. 여러 필터는 AND로 결합됩니다.
            - `stage`: 특정 컬럼만 보고 싶을 때 사용합니다. 다른 Stage는 빈 컬럼으로 응답됩니다.
            - `tag`: 특정 태그가 붙은 카드만 표시할 때 사용합니다.
            - `priority`: HIGH 카드만 모아 보기 등에 사용합니다.

            **호출 순서 팁**
            - 보드 진입 시: `GET /api/stages` (옵션) → `GET /api/applications` 한 번이면 충분합니다.
              이 API의 응답에 Stage 정보가 함께 들어 있으므로, 보드를 그리는 데 추가 호출이 필요 없습니다.
            - 태그 픽커가 필요하면 `GET /api/tags`를 별도로 호출하세요.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. 프론트는 `/api/auth/refresh`를 호출해 토큰을 갱신한 뒤 재시도하세요.",
            ),
            SwaggerErrorResponse(
                ErrorCode.INVALID_INPUT,
                description = "쿼리 파라미터 형식 오류. 예: `priority=URGENT`처럼 enum 외 값이 들어온 경우.",
            ),
        ],
    )
    fun getBoard(
        @Parameter(hidden = true) userId: Long,
        @Parameter(
            description = "특정 Stage ID에 속한 카드만 조회 (다른 Stage는 빈 컬럼). " +
                "값은 `GET /api/stages` 응답의 `id`를 사용하세요.",
            example = "2",
        ) stage: Long?,
        @Parameter(
            description = "특정 Tag ID가 연결된 카드만 조회. 값은 `GET /api/tags` 응답의 `id`.",
            example = "5",
        ) tag: Long?,
        @Parameter(
            description = "우선순위 필터. 가능한 값: `LOW`, `NORMAL`, `HIGH`.",
            example = "HIGH",
        ) priority: Priority?,
    ): ApiResponse<BoardResponse>

    @Operation(
        summary = "채용 카드 생성",
        description = """
            "지원 추가" 모달에서 호출합니다.

            **요청 본문 핵심**
            - `stageId`: 카드를 어느 컬럼에 둘지. `GET /api/stages` 응답의 `id`여야 합니다.
            - `tagIds`: 부착할 태그 ID 목록. 비우거나 생략하면 태그 없이 생성됩니다.
            - `deadlineAt`: 마감일(UTC ISO-8601 instant). 값을 주면 달력 도메인의
              `JOB_POSTING` 일정이 같은 트랜잭션에서 자동 생성됩니다 → 다음 `GET /api/schedule/calendar` 호출 시 함께 보입니다.
            - `priority`: 생략 시 `NORMAL`. `noResponseDays`는 무응답 기준일이며 생략 시 7일.

            **성공 응답**
            - HTTP `201 Created`, `data`에 생성된 카드 요약(`ApplicationCard`).
            - 보드 캐시를 가진 프론트라면 받은 카드를 `stageId` 컬럼 끝에 끼워 넣으면 됩니다 (전체 보드 재조회 불필요).
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
                description = "본문 검증 실패. `companyName`/`position`이 비었거나 길이 초과, `noResponseDays<0`, " +
                    "`jobPostingUrl`이 2048자 초과 등. 폼 필드 단위로 에러 메시지 노출 권장.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_NOT_FOUND,
                description = "`stageId`가 존재하지 않거나 본인 소유 Stage가 아닙니다. " +
                    "보드를 다시 불러와 Stage 목록을 동기화한 뒤 재시도 안내.",
            ),
            SwaggerErrorResponse(
                ErrorCode.TAG_NOT_FOUND,
                description = "`tagIds`에 본인 소유가 아닌(또는 삭제된) Tag가 포함됨. " +
                    "태그 목록을 다시 불러오고 사용자에게 선택 항목을 갱신하도록 안내.",
            ),
        ],
    )
    fun createApplication(
        @Parameter(hidden = true) userId: Long,
        request: CreateApplicationRequest,
    ): ResponseEntity<ApiResponse<ApplicationCard>>

    @Operation(
        summary = "채용 카드 수정",
        description = """
            카드 상세 모달의 저장 버튼, 또는 보드 드래그앤드롭(컬럼 간 이동)에서 호출합니다.

            **부분 수정 규칙**
            - 모든 필드 옵셔널. `null`이면 "변경하지 않음"을 의미합니다.
            - **단, `tagIds`만 예외**:
              - `null` → 태그 변경 없음
              - `[]` (빈 배열) → 태그 모두 제거
              - `[1, 5, 7]` → 해당 ID들로 set (기존에 붙어 있던 다른 태그는 제거됨)

            **컬럼 이동(드래그앤드롭)**
            - `stageId`만 보내면 카드가 다른 Stage 컬럼으로 이동합니다.
              새 Stage가 본인 소유가 아니면 `STAGE_NOT_FOUND`로 응답합니다.

            **마감일과 달력 동기화**
            - `deadlineAt`을 변경하면 연결된 `JOB_POSTING` 일정의 `startAt`이 함께 갱신됩니다.
            - 마감일을 비우려면 (현재 정책상) 별도 처리가 필요할 수 있으니 백엔드와 합의 후 사용하세요.

            **성공 응답**
            - `data`에 수정 후 단건 `ApplicationResponse`. 보드 캐시의 해당 카드만 교체하세요.
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
                description = "본문 검증 실패. 길이 제약(memo 5000자, jobPostingUrl 2048자), `noResponseDays`(1~365) 등.",
            ),
            SwaggerErrorResponse(
                ErrorCode.APPLICATION_NOT_FOUND,
                description = "카드가 없거나 본인 소유가 아닙니다. 보드를 다시 불러와 동기화 후 재시도 안내.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_NOT_FOUND,
                description = "`stageId` 변경 시 새 Stage가 본인 소유가 아닐 때. UI에서 컬럼을 갱신 후 다시 드래그하도록 안내.",
            ),
            SwaggerErrorResponse(
                ErrorCode.TAG_NOT_FOUND,
                description = "`tagIds`에 본인 소유가 아닌 Tag가 포함됨. 태그 픽커 동기화 후 재시도.",
            ),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 채용 카드 ID. 보드 응답의 `applications[].id`.", example = "101") id: Long,
        request: UpdateApplicationRequest,
    ): ApiResponse<ApplicationResponse>

    @Operation(
        summary = "채용 카드 삭제",
        description = """
            카드 삭제 확인 모달에서 호출합니다.

            **삭제 시 함께 정리되는 데이터** (프론트가 별도 호출 불필요)
            - 연결된 태그 매핑(`application_tags`)
            - 카드의 모든 회고(`Retrospective`)
            - 카드와 연결된 모든 일정(`JOB_POSTING`/`APPLICATION_PROCESS`)
            - 알림 큐의 마감/일정 알림

            **응답**
            - HTTP `204 No Content`, 본문 없음.
            - 프론트는 보드 캐시에서 해당 카드를 제거하면 됩니다.

            **본인 소유가 아닌 카드는 404로 응답**합니다 (FORBIDDEN을 노출하지 않아 ID enumeration 차단).
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
                ErrorCode.APPLICATION_NOT_FOUND,
                description = "이미 삭제된 카드이거나 본인 소유가 아닙니다. " +
                    "프론트는 보드를 다시 불러와 카드 목록을 동기화하세요.",
            ),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 채용 카드 ID.", example = "101") id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "회고 생성 (빈 회고)",
        description = """
            카드 상세에서 "회고 작성" 버튼 클릭 시 호출합니다. 빈 회고 1건을 만들어 응답합니다.

            **요청 본문**
            - `stageId` 있음 → **단계별 회고** (예: "1차 면접 직후")
            - `stageId` 생략/null → **카드 종합 회고** (지원 전체 정리)
            - 작성 시점의 `stageId`가 스냅샷으로 저장되며, 이후 카드 단계가 바뀌어도 이 회고의 `stageId`는 변하지 않습니다.

            **성공 응답**
            - HTTP `201 Created`, `data`에 빈 회고(`items=[]`) + `version=0`.
            - 받은 `id`로 다음 중 하나로 항목을 채웁니다:
              - `POST /api/retrospectives/{id}/items` — 질문 한 줄씩 직접 추가
              - `POST /api/retrospectives/{id}/apply-template` — 사용자 템플릿 적용
              - `POST /api/retrospectives/ai-questions` → 받은 질문을 사용자 편집 후 `/items`로 추가
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
                ErrorCode.APPLICATION_NOT_FOUND,
                description = "카드가 없거나 본인 소유가 아닙니다. 보드 동기화 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_NOT_FOUND,
                description = "`stageId`로 보낸 Stage가 존재하지 않거나 본인 소유가 아닙니다. " +
                    "Stage 목록을 다시 불러와 picker를 갱신하세요.",
            ),
        ],
    )
    fun createRetrospective(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "회고를 생성할 채용 카드 ID.", example = "101") id: Long,
        request: CreateRetrospectiveRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveResponse>>
}

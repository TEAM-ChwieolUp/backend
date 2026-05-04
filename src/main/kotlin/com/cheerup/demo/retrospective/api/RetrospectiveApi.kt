package com.cheerup.demo.retrospective.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.AddRetrospectiveItemRequest
import com.cheerup.demo.retrospective.dto.ApplyRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveItemsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveListResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveItemRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(
    name = "Retrospectives",
    description = """
        채용 카드별 회고를 다루는 API. 회고는 **질문-답변 쌍의 목록(`items`)** 으로 구성됩니다.

        **항목(`items`) 식별 모델**
        - 각 항목은 ID가 없고 **현재 리스트에서의 0-base 인덱스**로 조작합니다.
        - 항목 추가/삭제 후에는 인덱스가 밀리므로, 응답으로 받은 `items` 배열을 그대로 새 상태로 사용하세요.

        **동시 편집 충돌**
        - 같은 회고를 동시에 수정하면 Optimistic Lock으로 1회 자동 재시도합니다. 두 번째도 실패하면 `409 RETROSPECTIVE_CONCURRENT_MODIFICATION`.
        - 응답의 `version`을 캐시해 두면 디버깅이 쉽지만, 클라이언트가 매 요청에 보낼 필요는 없습니다 (서버가 트랜잭션 내에서 자체 관리).
    """,
)
interface RetrospectiveApi {

    @Operation(
        summary = "카드별 회고 목록 조회",
        description = """
            칸반 카드 상세 모달의 "회고" 탭 진입 시 호출합니다.

            **응답**
            - `data.retrospectives[]`: 카드에 작성된 회고 요약 목록.
            - 항목 본문(`items`)은 포함되지 않으며 `itemCount`만 제공합니다 — 목록 화면에 미리보기 카운트만 노출하면 충분하기 때문입니다.
            - 본문이 필요한 시점에 `GET /api/retrospectives/{id}`로 단건 조회하세요.

            **정렬**: 백엔드 기본 정렬을 따릅니다 (현재 등록 시점 기준 / 추후 변경될 수 있음).
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
                description = "카드가 없거나 본인 소유가 아닙니다. 보드를 다시 불러와 동기화 후 재시도.",
            ),
        ],
    )
    fun listByApplication(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회할 채용 카드 ID. 보드 응답의 `applications[].id`.", example = "101") appId: Long,
    ): ApiResponse<RetrospectiveListResponse>

    @Operation(
        summary = "회고 단건 조회",
        description = """
            회고 목록에서 한 건 클릭해 상세 화면을 열 때 호출합니다.

            **응답에 포함되는 정보**
            - `items[]`: 질문-답변 쌍 전체 (각 항목은 `question`, `answer`).
            - `stageId`: null이면 카드 종합 회고, 값이 있으면 단계별 회고. 작성 시점의 단계가 스냅샷으로 보존됩니다.
            - `createdAt`/`updatedAt`: 표시용 타임스탬프. UTC ISO-8601 instant.

            **사용 팁**: 항목 편집 화면을 열 때 받은 `items`를 그대로 폼 초기값으로 쓰세요.
            이후 추가/수정/삭제 응답에도 동일 형태(`items[]`)가 다시 오므로 통째로 교체하면 됩니다.
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
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                description = "회고가 없거나 본인 소유가 아닙니다. 회고 목록을 다시 불러와 동기화.",
            ),
        ],
    )
    fun getOne(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회할 회고 ID.", example = "12") id: Long,
    ): ApiResponse<RetrospectiveResponse>

    @Operation(
        summary = "회고 삭제",
        description = """
            회고 상세의 삭제 버튼(확인 모달 후) 또는 카드별 회고 목록의 항목 메뉴에서 호출합니다.

            **연쇄 처리**: 회고 자체만 hard delete 합니다 (외부 시스템 정리 없음, 카드는 영향 없음).

            **응답**: HTTP `204 No Content`, 본문 없음.
            프론트는 회고 목록 캐시에서 해당 ID를 제거하면 됩니다.
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
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                description = "이미 삭제된 회고이거나 본인 소유가 아닙니다. 회고 목록을 다시 불러와 동기화.",
            ),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 회고 ID.", example = "12") id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "회고 항목 추가",
        description = """
            회고 편집 화면에서 "+ 항목 추가" 버튼 또는 AI 질문 채택 후 호출합니다. 항목 1건을 리스트 끝에 append 합니다.

            **요청 본문**
            - `question`: 1~1000자, 필수.
            - `answer`: 0~5000자, 옵션. 질문만 먼저 추가하고 나중에 답변을 채우는 흐름이 일반적입니다.

            **응답 (`RetrospectiveItemsResponse`)**
            - `items[]`: 변경 후 **전체** 항목 목록 (방금 추가된 항목이 마지막에 위치).
            - `version`: 낙관적 락 버전. 디버깅용으로 활용 가능.
            - 프론트는 응답으로 받은 `items`를 통째로 새 상태로 교체하면 됩니다 (인덱스 충돌 걱정 없음).

            **동시 편집 처리**: Optimistic Lock으로 1회 자동 재시도. 두 번째도 충돌하면 `409 RETROSPECTIVE_CONCURRENT_MODIFICATION`.
            프론트는 회고를 다시 불러와 사용자에게 "다른 변경사항이 있어 새로 받았어요" 안내 후 재입력 유도.
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
                description = "question이 비었거나 1000자 초과, answer가 5000자 초과 등. 폼 단위 에러 표시 권장.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                description = "회고가 없거나 본인 소유가 아닙니다. 회고 목록 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION,
                description = "동시 편집 충돌이 재시도 후에도 해결되지 않음. " +
                    "프론트는 `GET /api/retrospectives/{id}`로 최신 상태를 받아 사용자에게 보여주고 재입력을 유도하세요.",
            ),
        ],
    )
    fun addItem(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "항목을 추가할 회고 ID.", example = "12") id: Long,
        request: AddRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "회고 항목 수정",
        description = """
            회고 항목의 인라인 편집(주로 답변 채우기) 저장 시 호출합니다.

            **부분 수정**
            - `question`/`answer` 모두 옵셔널. `null` = 변경 없음.
            - **빈 본문(`{}`)도 허용** — 변경 없이 200 응답 (no-op).

            **인덱스 규칙**
            - `index`는 현재 `items` 리스트의 **0-base 위치**.
              방금 받은 회고의 `items[3]`을 수정하려면 `index=3`.
            - 다른 사용자의 추가/삭제로 인덱스가 밀린 상태라면 잘못된 항목을 수정할 수 있으니,
              가능하면 즉시 응답의 `items`로 화면을 갱신해 주세요.

            **응답**: `RetrospectiveItemsResponse`(전체 `items` + `version`). 통째로 교체.

            **인덱스가 범위 밖이면** `404 RETROSPECTIVE_ITEM_INDEX_INVALID`.
            **동시 편집 충돌**: 1회 자동 재시도, 두 번째도 실패 시 `409 RETROSPECTIVE_CONCURRENT_MODIFICATION`.
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
                description = "question 1000자 초과, answer 5000자 초과 등 길이 제약 위반.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                description = "회고가 없거나 본인 소유가 아닙니다. 회고 목록 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_ITEM_INDEX_INVALID,
                description = "`index`가 현재 `items` 길이보다 크거나 음수입니다. " +
                    "회고를 다시 불러와 화면을 동기화하세요.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION,
                description = "동시 편집 충돌이 재시도 후에도 해결되지 않음. 회고를 다시 불러와 사용자에게 보여주고 재입력 유도.",
            ),
        ],
    )
    fun updateItem(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "회고 ID.", example = "12") id: Long,
        @Parameter(
            description = "수정할 항목의 0-base 인덱스. 가장 최근에 받은 `items` 배열 기준.",
            example = "0",
        ) index: Int,
        request: UpdateRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "회고 항목 삭제",
        description = """
            회고 항목 옆 삭제 버튼에서 호출합니다.

            **인덱스 규칙**: `updateItem`과 동일하게 0-base.

            **응답 후 처리**
            - 응답의 `items[]`는 이미 해당 항목이 빠진 상태이며, 그 뒤 항목들의 인덱스는 -1씩 당겨져 있습니다.
            - 프론트는 응답으로 통째로 화면을 갱신하면 인덱스 정합성 문제가 없습니다.

            **인덱스가 범위 밖이면** `404 RETROSPECTIVE_ITEM_INDEX_INVALID`.
            **동시 편집 충돌**: 1회 자동 재시도, 두 번째도 실패 시 `409 RETROSPECTIVE_CONCURRENT_MODIFICATION`.
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
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                description = "회고가 없거나 본인 소유가 아닙니다. 회고 목록 갱신 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_ITEM_INDEX_INVALID,
                description = "`index`가 현재 `items` 길이보다 크거나 음수입니다. 회고를 다시 불러와 동기화.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_CONCURRENT_MODIFICATION,
                description = "동시 편집 충돌이 재시도 후에도 해결되지 않음. 회고를 다시 불러와 재시도 안내.",
            ),
        ],
    )
    fun deleteItem(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "회고 ID.", example = "12") id: Long,
        @Parameter(
            description = "삭제할 항목의 0-base 인덱스. 가장 최근에 받은 `items` 배열 기준.",
            example = "0",
        ) index: Int,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "AI 회고 질문 생성",
        description = """
            회고 편집 화면의 "AI 질문 받기" 버튼에서 호출합니다.

            **동작**
            - 카드의 회사명·포지션·메모, 옵션으로 받은 `stageId`의 단계 정보를 컨텍스트로 LLM이 회고 질문 목록을 생성합니다.
            - **결과는 DB에 저장되지 않습니다** (Suggestion 패턴 없음).

            **요청 본문**
            - `applicationId`: 컨텍스트로 사용할 채용 카드 ID. 양수, 본인 소유여야 합니다.
            - `stageId`: 단계별 회고용 컨텍스트. 생략하면 종합 회고용 질문이 생성됩니다.

            **응답**
            - `data.questions[]`: 생성된 질문 문자열 목록.
            - 프론트는 이 목록을 사용자에게 체크박스/편집 가능한 형태로 보여주고,
              사용자가 선별·편집한 항목을 `POST /api/retrospectives/{id}/items`로 한 건씩 추가하세요.

            **레이트 리밋 / 외부 호출 실패**
            - `429 RATE_LIMITED` 시: "AI 호출 한도를 넘었어요. 잠시 후 다시 시도해 주세요" 안내 + 버튼 잠시 비활성화.
            - `502 AI_GENERATION_FAILED` / `504 AI_GENERATION_TIMEOUT` 시: 일시적 외부 장애. 재시도 버튼 + "직접 질문을 입력하거나 템플릿을 사용해 보세요" 폴백 안내.
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
                description = "applicationId/stageId가 양수가 아니거나 누락 등 본문 검증 실패.",
            ),
            SwaggerErrorResponse(
                ErrorCode.APPLICATION_NOT_FOUND,
                description = "`applicationId`로 보낸 카드가 없거나 본인 소유가 아닙니다.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_NOT_FOUND,
                description = "`stageId`로 보낸 단계가 없거나 본인 소유가 아닙니다. " +
                    "단계 picker를 다시 불러와 동기화하세요.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RATE_LIMITED,
                description = "계정당 AI 호출 한도 초과. 사용자에게 \"잠시 후 다시 시도해 주세요\" 안내 + 버튼 일시 비활성화.",
            ),
            SwaggerErrorResponse(
                ErrorCode.AI_GENERATION_FAILED,
                description = "외부 LLM 호출 실패(502). 재시도 버튼 노출 + 직접 입력/템플릿 사용 폴백 권장.",
            ),
            SwaggerErrorResponse(
                ErrorCode.AI_GENERATION_TIMEOUT,
                description = "외부 LLM 응답 타임아웃(504). 재시도 또는 폴백 안내.",
            ),
        ],
    )
    fun generateAiQuestions(
        @Parameter(hidden = true) userId: Long,
        request: RetrospectiveQuestionRequest,
    ): ApiResponse<RetrospectiveQuestionsResponse>

    @Operation(
        summary = "회고 템플릿 적용",
        description = """
            회고 편집 화면에서 "템플릿 적용" → 사용자 템플릿 picker → 선택 시 호출합니다.

            **동작**
            - 선택한 템플릿의 `questions[]`를 회고 `items` **끝에 append** 합니다 (덮어쓰기 아님). 답변은 `null`로 초기화됩니다.
            - **스냅샷 복사** — 적용 시점의 questions가 회고에 박힙니다. 이후 템플릿이 수정/삭제돼도 이미 적용된 회고에는 영향 없음.
            - 여러 템플릿을 연달아 적용하면 항목이 누적됩니다.

            **요청 본문**: `templateId`(양수). `GET /api/retrospective-templates`로 받은 ID.

            **응답**: 적용 후 회고 단건(`RetrospectiveResponse`, items 포함). 화면을 통째로 갱신하세요.

            **권한**: 회고와 템플릿 **모두 요청자 소유**여야 합니다. 둘 중 하나라도 아니면 404로 응답합니다.
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
                description = "`templateId`가 누락되거나 양수가 아님.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                description = "회고가 없거나 본인 소유가 아닙니다. 회고 목록 동기화 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND,
                description = "템플릿이 없거나 본인 소유가 아닙니다. 템플릿 목록을 다시 불러와 picker를 갱신하세요.",
            ),
        ],
    )
    fun applyTemplate(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "템플릿을 적용할 회고 ID.", example = "12") id: Long,
        request: ApplyRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveResponse>
}

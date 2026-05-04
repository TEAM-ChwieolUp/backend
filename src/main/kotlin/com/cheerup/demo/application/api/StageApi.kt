package com.cheerup.demo.application.api

import com.cheerup.demo.application.dto.CreateStageRequest
import com.cheerup.demo.application.dto.StageResponse
import com.cheerup.demo.application.dto.UpdateStageRequest
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(
    name = "Stages",
    description = "칸반 보드의 컬럼(Stage) CRUD API. " +
        "사용자별로 자유롭게 추가/이름변경/순서변경할 수 있지만, `PASSED`(최종 합격)와 `REJECTED`(불합격)는 " +
        "시스템 고정 단계로 항상 보드의 가장 오른쪽 두 자리를 차지합니다.",
)
interface StageApi {

    @Operation(
        summary = "Stage 목록 조회",
        description = """
            보드의 컬럼 헤더만 따로 그릴 때 또는 카드 생성/이동 시 Stage picker를 채울 때 호출합니다.

            **응답**
            - `data[]`: 사용자가 소유한 모든 Stage를 `displayOrder` 오름차순으로 반환.
            - 각 Stage의 `category`:
              - `IN_PROGRESS` — 사용자가 자유롭게 만들 수 있는 진행 단계 (서류/코딩테스트/면접 등)
              - `PASSED` — 시스템 고정 (최종 합격)
              - `REJECTED` — 시스템 고정 (불합격)

            **참고**: 보드 메인 진입에는 `GET /api/applications`만으로도 Stage 정보가 함께 오므로 별도 호출이 불필요합니다.
            본 API는 카드/회고 생성 모달의 Stage picker 같은 보조 화면용입니다.
        """,
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(
                ErrorCode.UNAUTHORIZED,
                description = "JWT 누락/만료. `/api/auth/refresh`로 갱신 후 재시도.",
            ),
        ],
    )
    fun list(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<List<StageResponse>>

    @Operation(
        summary = "Stage 생성",
        description = """
            보드 우측 "+ 단계 추가" 버튼에서 호출합니다.

            **자동 처리**
            - `category`는 입력 받지 않으며 항상 `IN_PROGRESS`로 생성됩니다.
            - `displayOrder`를 생략하면 **`PASSED` 직전 자리**에 삽입되고, `PASSED`/`REJECTED`는 한 칸씩 오른쪽으로 시프트됩니다 → "고정 단계는 항상 맨 오른쪽 두 자리" 불변식이 유지됩니다.
            - `color`는 6자리 hex(`#RRGGBB`) 형식이어야 합니다.

            **응답**
            - HTTP `201 Created`, `data`에 생성된 `StageResponse`.
            - 보드 캐시에 새 Stage를 `displayOrder` 위치에 삽입하면 됩니다.
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
                description = "이름이 비었거나 30자 초과, color 형식 오류, displayOrder가 음수 등. 폼 단위 에러 표시 권장.",
            ),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: Long,
        request: CreateStageRequest,
    ): ResponseEntity<ApiResponse<StageResponse>>

    @Operation(
        summary = "Stage 수정",
        description = """
            컬럼 헤더 메뉴에서 "이름 변경"/"색상 변경"이나, 보드 컬럼 자체를 드래그해 순서를 바꿀 때 호출합니다.

            **부분 수정**: 모든 필드 옵셔널. `null` = 변경 없음. 빈 본문(`{}`)은 변경 없이 200.

            **수정 가능 필드**: `name`, `color`, `displayOrder` 만 수정 가능합니다.
            **수정 불가**: `category` (시스템 관리 항목, val).

            **순서 변경 제약 (`STAGE_ORDER_PROTECTED`)**
            - `PASSED`/`REJECTED`의 `displayOrder`는 변경 불가.
            - `IN_PROGRESS` Stage를 `PASSED`/`REJECTED`보다 오른쪽으로 옮길 수 없음.
            - 위반 시 409 응답. 프론트는 드래그 이동을 되돌리고 "고정 단계 오른쪽으로 이동할 수 없습니다" 토스트.
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
                description = "이름 길이 초과(30자), color 형식 오류, displayOrder 음수 등.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_NOT_FOUND,
                description = "Stage가 없거나 본인 소유가 아닙니다. 보드를 다시 불러와 동기화하세요.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_ORDER_PROTECTED,
                description = "고정 단계(PASSED/REJECTED)의 순서를 바꾸려 했거나, IN_PROGRESS를 고정 단계보다 오른쪽으로 옮기려 함. " +
                    "프론트는 드래그를 원래 자리로 되돌리고 안내 메시지를 띄우세요.",
            ),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 Stage ID.", example = "2") id: Long,
        request: UpdateStageRequest,
    ): ApiResponse<StageResponse>

    @Operation(
        summary = "Stage 삭제",
        description = """
            컬럼 헤더 메뉴 "단계 삭제" 클릭 시 호출합니다.

            **삭제 가능 조건** (모두 만족해야 함)
            1. `category`가 `IN_PROGRESS`일 것 — `PASSED`/`REJECTED`는 항상 삭제 불가
            2. 그 Stage에 카드가 0개일 것

            **프론트 사전 차단 권장**
            - 보드에 이미 각 컬럼의 카드 개수와 `category`가 있으므로,
              `category != IN_PROGRESS`이거나 카드 ≥1개면 삭제 버튼을 disabled 처리하세요.

            **응답**: HTTP `204 No Content`, 본문 없음. 보드 캐시에서 해당 Stage 제거.
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
                ErrorCode.STAGE_NOT_FOUND,
                description = "이미 삭제된 Stage이거나 본인 소유가 아닙니다. 보드를 다시 불러와 동기화.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_FIXED,
                description = "PASSED/REJECTED는 시스템 고정 단계라 삭제 불가. " +
                    "프론트는 이 두 카테고리에 대해 삭제 버튼을 노출하지 않는 게 정상이며, " +
                    "이 응답이 오면 사용자에게 \"최종합격/불합격 단계는 삭제할 수 없습니다\" 안내.",
            ),
            SwaggerErrorResponse(
                ErrorCode.STAGE_NOT_EMPTY,
                description = "이 Stage에 카드가 남아 있어 삭제 불가. " +
                    "프론트는 \"카드 N개를 먼저 다른 단계로 옮기거나 삭제해주세요\" 안내.",
            ),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 Stage ID.", example = "2") id: Long,
    ): ResponseEntity<Void>
}

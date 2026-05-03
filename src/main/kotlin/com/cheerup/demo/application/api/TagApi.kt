package com.cheerup.demo.application.api

import com.cheerup.demo.application.dto.CreateTagRequest
import com.cheerup.demo.application.dto.TagResponse
import com.cheerup.demo.application.dto.UpdateTagRequest
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
    name = "Tags",
    description = "칸반 카드에 부착하는 태그 CRUD API. " +
        "태그는 사용자별이며 같은 사용자 내에서 이름이 unique 합니다 (`(user_id, name)` UNIQUE).",
)
interface TagApi {

    @Operation(
        summary = "태그 목록 조회",
        description = """
            카드 생성/수정 모달의 태그 picker를 채울 때, 또는 보드 상단의 태그 필터 드롭다운에 사용합니다.

            **응답**
            - `data[]`: 사용자가 소유한 모든 태그를 ID 오름차순으로 반환.
            - 응답 항목으로 `id`/`name`/`color`만 옵니다 — 카드 부착 정보는 포함되지 않습니다.

            **참고**: 카드별 태그 정보는 `GET /api/applications` 응답의 `applications[].tags[]`에 함께 옵니다.
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
    ): ApiResponse<List<TagResponse>>

    @Operation(
        summary = "태그 생성",
        description = """
            태그 관리 화면 또는 카드 모달의 "+ 새 태그" UI에서 호출합니다.

            **요청 본문**
            - `name`: 1~30자, 같은 사용자 내 unique. 중복이면 409 응답.
            - `color`: 6자리 hex(`#RRGGBB`).

            **응답**
            - HTTP `201 Created`, `data`에 생성된 `TagResponse`.
            - 카드 모달에서 호출했다면 받은 태그를 picker 목록에 추가하고 자동 선택 상태로 두는 UX가 자연스럽습니다.
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
                description = "이름이 비었거나 30자 초과, color 형식 오류. 폼 단위 에러 표시 권장.",
            ),
            SwaggerErrorResponse(
                ErrorCode.TAG_DUPLICATE,
                description = "같은 이름의 태그가 이미 존재. 프론트는 \"이미 존재하는 태그명입니다\" 토스트 + 입력값 유지.",
            ),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: Long,
        request: CreateTagRequest,
    ): ResponseEntity<ApiResponse<TagResponse>>

    @Operation(
        summary = "태그 수정",
        description = """
            태그 관리 화면에서 이름/색상 편집 시 호출합니다.

            **부분 수정**: 모든 필드 옵셔널. `null` = 변경 없음. 빈 본문(`{}`)은 변경 없이 200.

            **응답**: 수정 후 `TagResponse`. 보드 캐시에 같은 ID로 표시되는 모든 카드의 태그도 같이 갱신해야 시각적으로 반영됩니다.
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
                description = "이름 길이 초과(30자), color 형식 오류 등.",
            ),
            SwaggerErrorResponse(
                ErrorCode.TAG_NOT_FOUND,
                description = "태그가 없거나 본인 소유가 아닙니다. 태그 목록을 다시 불러와 동기화.",
            ),
            SwaggerErrorResponse(
                ErrorCode.TAG_DUPLICATE,
                description = "이름 변경으로 동일 이름 충돌이 발생. 프론트는 입력값 유지 + \"이미 존재하는 태그명\" 안내.",
            ),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 태그 ID.", example = "5") id: Long,
        request: UpdateTagRequest,
    ): ApiResponse<TagResponse>

    @Operation(
        summary = "태그 삭제",
        description = """
            태그 관리 화면에서 삭제 시 호출합니다.

            **연쇄 처리**
            - 이 태그가 부착된 모든 카드의 부착 정보(`application_tags`)는 DB FK CASCADE로 자동 삭제됩니다.
            - 카드 자체는 삭제되지 않습니다.
            - 프론트는 보드 캐시 내 모든 카드의 `tags[]`에서 이 태그 ID를 제거해야 합니다.

            **응답**: HTTP `204 No Content`, 본문 없음.
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
                ErrorCode.TAG_NOT_FOUND,
                description = "이미 삭제된 태그이거나 본인 소유가 아닙니다. 태그 목록을 다시 불러와 동기화.",
            ),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 태그 ID.", example = "5") id: Long,
    ): ResponseEntity<Void>
}

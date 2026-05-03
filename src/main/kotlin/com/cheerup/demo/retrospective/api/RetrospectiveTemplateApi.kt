package com.cheerup.demo.retrospective.api

import com.cheerup.demo.global.config.swagger.SwaggerErrorResponse
import com.cheerup.demo.global.config.swagger.SwaggerErrorResponses
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveTemplateResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveTemplateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(
    name = "Retrospective Templates",
    description = """
        사용자가 자주 쓰는 회고 질문 묶음을 저장해 두고 회고에 한 번에 적용할 수 있는 템플릿 CRUD API.

        **특성**
        - 템플릿은 **답변 없이 질문 목록만** 저장합니다. 답변은 회고 작성 시점에 채웁니다.
        - 같은 사용자 내 `name`은 unique 합니다. 중복 시 `409 RETROSPECTIVE_TEMPLATE_DUPLICATE`.
        - 적용은 별도 API: `POST /api/retrospectives/{id}/apply-template` 사용.
        - 적용은 **스냅샷 복사**라, 이후 본 템플릿을 수정/삭제해도 이미 적용된 회고에는 영향 없습니다.
    """,
)
interface RetrospectiveTemplateApi {

    @Operation(
        summary = "템플릿 목록 조회",
        description = """
            회고 편집 화면의 "템플릿 적용" picker, 또는 마이페이지의 템플릿 관리 화면에서 호출합니다.

            **응답**: 사용자가 소유한 모든 템플릿을 한 번에 반환합니다 (`questions` 포함).
            템플릿 수가 많지 않은 가벼운 도메인이라 페이지네이션은 두지 않았습니다.

            picker UX 권장: 템플릿 이름과 함께 `questions.size`(질문 개수) 미리보기를 보여주면 사용자가 선택하기 쉽습니다.
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
    ): ApiResponse<List<RetrospectiveTemplateResponse>>

    @Operation(
        summary = "템플릿 단건 조회",
        description = """
            템플릿 편집 화면 진입 시 호출합니다. `questions[]` 전체를 폼 초기값으로 사용하세요.

            본인 소유가 아니거나 존재하지 않으면 404로 응답하므로,
            템플릿 목록을 다시 불러와 picker를 갱신하도록 사용자를 안내하세요.
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
                ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND,
                description = "템플릿이 없거나 본인 소유가 아닙니다. 템플릿 목록을 다시 불러와 동기화.",
            ),
        ],
    )
    fun get(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "조회할 템플릿 ID.", example = "3") id: Long,
    ): ApiResponse<RetrospectiveTemplateResponse>

    @Operation(
        summary = "템플릿 생성",
        description = """
            템플릿 관리 화면 또는 회고 편집 화면 안의 "현재 회고를 템플릿으로 저장" UI에서 호출합니다.

            **요청 본문**
            - `name`: 1~50자, 사용자 단위 unique. 중복이면 `409 RETROSPECTIVE_TEMPLATE_DUPLICATE`.
            - `questions[]`: 최대 50개. 빈 배열도 허용 (질문은 나중에 추가 가능).
              빈 문자열은 서버에서 자동으로 제거되지만, 가능하면 프론트에서 미리 trim/필터링 해 보내세요.

            **응답**: HTTP `201 Created`, `data`에 생성된 `RetrospectiveTemplateResponse`.
            템플릿 picker 캐시에 받은 항목을 추가하면 됩니다.
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
                description = "name이 비었거나 50자 초과, questions가 50개 초과 등.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE,
                description = "같은 이름의 템플릿이 이미 존재. " +
                    "프론트는 입력값 유지 + \"이미 존재하는 템플릿 이름입니다\" 안내.",
            ),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: Long,
        request: CreateRetrospectiveTemplateRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveTemplateResponse>>

    @Operation(
        summary = "템플릿 수정",
        description = """
            템플릿 편집 화면의 저장 버튼에서 호출합니다.

            **부분 수정**
            - `name`/`questions` 모두 옵셔널. `null` = 변경 없음. 빈 본문(`{}`)도 허용.
            - `questions`를 빈 배열(`[]`)로 보내면 모든 질문이 제거됩니다 (전체 교체 의미).

            **이미 적용된 회고는 영향 없음** — 회고 적용은 스냅샷이라 본 수정은 미래의 적용에만 반영됩니다.
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
                description = "name 길이 초과(50자), questions 개수 초과(50개) 등.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND,
                description = "템플릿이 없거나 본인 소유가 아닙니다. 템플릿 목록 동기화 후 재시도.",
            ),
            SwaggerErrorResponse(
                ErrorCode.RETROSPECTIVE_TEMPLATE_DUPLICATE,
                description = "이름 변경으로 동일 이름 충돌. 프론트는 입력값 유지 + 안내 문구 노출.",
            ),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 템플릿 ID.", example = "3") id: Long,
        request: UpdateRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveTemplateResponse>

    @Operation(
        summary = "템플릿 삭제",
        description = """
            템플릿 관리 화면의 삭제 버튼에서 호출합니다.

            **이미 적용된 회고는 영향 없음** — 회고에는 스냅샷으로 복사된 항목들이 그대로 남습니다.

            **응답**: HTTP `204 No Content`, 본문 없음.
            프론트는 템플릿 picker 캐시에서 해당 ID를 제거하세요.
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
                ErrorCode.RETROSPECTIVE_TEMPLATE_NOT_FOUND,
                description = "이미 삭제된 템플릿이거나 본인 소유가 아닙니다. 템플릿 목록을 다시 불러와 동기화.",
            ),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 템플릿 ID.", example = "3") id: Long,
    ): ResponseEntity<Void>
}

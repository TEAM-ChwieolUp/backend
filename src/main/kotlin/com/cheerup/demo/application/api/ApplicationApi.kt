package com.cheerup.demo.application.api

import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.dto.ApplicationCard
import com.cheerup.demo.application.dto.ApplicationResponse
import com.cheerup.demo.application.dto.BoardResponse
import com.cheerup.demo.application.dto.CreateApplicationRequest
import com.cheerup.demo.application.dto.UpdateApplicationRequest
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.global.response.ErrorResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
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

@Tag(name = "Applications", description = "채용 칸반 보드 카드 API")
interface ApplicationApi {

    @Operation(
        summary = "채용 칸반 보드 조회",
        description = "Stage 목록과 각 Stage에 속한 채용 카드를 조회합니다. stage, tag, priority 조건으로 필터링할 수 있습니다.",
        parameters = [
            Parameter(
                name = "stage",
                description = "특정 Stage ID에 속한 카드만 조회합니다.",
                `in` = ParameterIn.QUERY,
                example = "2",
            ),
            Parameter(
                name = "tag",
                description = "특정 Tag ID가 연결된 카드만 조회합니다.",
                `in` = ParameterIn.QUERY,
                example = "5",
            ),
            Parameter(
                name = "priority",
                description = "우선순위로 필터링합니다.",
                `in` = ParameterIn.QUERY,
                schema = Schema(allowableValues = ["LOW", "NORMAL", "HIGH"]),
                example = "HIGH",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "조회 성공",
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "잘못된 요청 파라미터",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun getBoard(
        userId: Long,
        stage: Long?,
        tag: Long?,
        priority: Priority?,
    ): ApiResponse<BoardResponse>

    @Operation(
        summary = "채용 카드 생성",
        description = "새 채용 카드를 생성합니다. stageId와 tagIds는 요청 사용자가 소유한 리소스여야 합니다.",
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateApplicationRequest::class),
                    examples = [
                        ExampleObject(
                            name = "createApplication",
                            value = """{"stageId":1,"companyName":"네이버","position":"Backend Developer","appliedAt":"2026-04-20T00:00:00Z","deadlineAt":"2026-05-10T14:00:00Z","noResponseDays":7,"priority":"HIGH","memo":"서류 제출 완료","jobPostingUrl":"https://careers.example.com/jobs/101","tagIds":[5,7]}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "201",
        description = "생성 성공",
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Stage 또는 Tag를 찾을 수 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun createApplication(
        userId: Long,
        request: CreateApplicationRequest,
    ): ResponseEntity<ApiResponse<ApplicationCard>>

    @Operation(
        summary = "채용 카드 수정",
        description = "채용 카드의 필드를 부분 수정합니다. null 필드는 변경하지 않으며, tagIds가 빈 배열이면 모든 태그를 제거합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "수정할 채용 카드 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "101",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UpdateApplicationRequest::class),
                    examples = [
                        ExampleObject(
                            name = "updateApplication",
                            value = """{"stageId":2,"priority":"NORMAL","memo":"면접 일정 조율 중","tagIds":[5]}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "수정 성공",
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Application, Stage 또는 Tag를 찾을 수 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun update(
        userId: Long,
        id: Long,
        request: UpdateApplicationRequest,
    ): ApiResponse<ApplicationResponse>

    @Operation(
        summary = "채용 카드 삭제",
        description = "채용 카드를 삭제합니다. 연결된 태그(application_tags)는 함께 정리됩니다. 본인 소유가 아닌 카드는 404로 응답합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "삭제할 채용 카드 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "101",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "204",
        description = "삭제 성공 (응답 본문 없음)",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Application을 찾을 수 없거나 본인 소유가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun delete(
        userId: Long,
        id: Long,
    ): ResponseEntity<Void>

    @Operation(
        summary = "회고 생성 (빈 회고)",
        description = "지정한 채용 카드에 빈 회고를 생성합니다. stageId가 있으면 단계별 회고, 없으면 카드 종합 회고가 됩니다. 라우팅만 본 도메인이 받고 본문은 retrospective/ 도메인이 처리합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "회고를 생성할 채용 카드 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "101",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateRetrospectiveRequest::class),
                    examples = [
                        ExampleObject(
                            name = "createRetrospectiveWithStage",
                            value = """{"stageId":5}""",
                        ),
                        ExampleObject(
                            name = "createOverallRetrospective",
                            value = """{}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "201",
        description = "생성 성공 (items=[])",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Application 또는 Stage를 찾을 수 없음 (본인 소유가 아닌 경우 포함)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun createRetrospective(
        userId: Long,
        id: Long,
        request: CreateRetrospectiveRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveResponse>>
}

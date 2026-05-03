package com.cheerup.demo.retrospective.api

import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.global.response.ErrorResponse
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
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Retrospectives", description = "회고 조회 API")
interface RetrospectiveApi {

    @Operation(
        summary = "카드별 회고 목록 조회",
        description = "특정 채용 카드에 작성된 회고 목록을 조회합니다. 응답에는 항목 본문(items)이 포함되지 않으며 itemCount만 노출됩니다.",
        parameters = [
            Parameter(
                name = "appId",
                description = "조회할 채용 카드 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "101",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "조회 성공",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "채용 카드를 찾을 수 없거나 본인 소유가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun listByApplication(
        userId: Long,
        appId: Long,
    ): ApiResponse<RetrospectiveListResponse>

    @Operation(
        summary = "회고 단건 조회",
        description = "회고 단건을 항목 본문(items) 포함하여 조회합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "조회할 회고 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "12",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "조회 성공",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "회고를 찾을 수 없거나 본인 소유가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun getOne(
        userId: Long,
        id: Long,
    ): ApiResponse<RetrospectiveResponse>

    @Operation(
        summary = "회고 삭제",
        description = "회고를 hard delete 합니다. 외부 시스템 정리는 없습니다. 본인 소유가 아닌 회고는 404로 응답합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "삭제할 회고 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "12",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "204",
        description = "삭제 성공 (응답 본문 없음)",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "회고를 찾을 수 없거나 본인 소유가 아님",
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
        summary = "회고 항목 추가",
        description = "회고에 질문-답변 쌍을 1건 append 합니다. 응답은 변경 후 전체 items와 @Version 값을 함께 반환합니다. 동시 변경 충돌 시 1회 자동 재시도되며, 두 번째도 실패하면 409 RETROSPECTIVE_CONCURRENT_MODIFICATION 으로 응답합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "항목을 추가할 회고 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "12",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AddRetrospectiveItemRequest::class),
                    examples = [
                        ExampleObject(
                            name = "addRetrospectiveItemQuestionOnly",
                            value = """{"question":"면접관 인상은?"}""",
                        ),
                        ExampleObject(
                            name = "addRetrospectiveItemWithAnswer",
                            value = """{"question":"잘한 점은?","answer":"라이브 코딩 침착하게 풀이"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "추가 성공 (전체 items + version 반환)",
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패 (question blank, 길이 초과 등)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "회고를 찾을 수 없거나 본인 소유가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "동시 변경 충돌 (재시도 후에도 실패)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun addItem(
        userId: Long,
        id: Long,
        request: AddRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "회고 항목 수정",
        description = "인덱스 위치 항목의 question/answer 를 부분 수정합니다. 빈 본문(`{}`)은 변경 없이 200으로 응답합니다. 인덱스가 범위를 벗어나면 404 RETROSPECTIVE_ITEM_INDEX_INVALID 로 응답합니다. 동시 변경 충돌 시 1회 자동 재시도되며, 두 번째도 실패하면 409 RETROSPECTIVE_CONCURRENT_MODIFICATION 으로 응답합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "회고 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "12",
            ),
            Parameter(
                name = "index",
                description = "수정할 항목의 0-base 인덱스",
                required = true,
                `in` = ParameterIn.PATH,
                example = "0",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UpdateRetrospectiveItemRequest::class),
                    examples = [
                        ExampleObject(
                            name = "updateAnswerOnly",
                            value = """{"answer":"차분하고 친절하셨음"}""",
                        ),
                        ExampleObject(
                            name = "updateQuestionAndAnswer",
                            value = """{"question":"면접관 인상은?","answer":"차분함"}""",
                        ),
                        ExampleObject(
                            name = "noOp",
                            value = """{}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "수정 성공 (전체 items + version 반환)",
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패 (question 길이 초과 등)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "회고를 찾을 수 없거나 본인 소유가 아님 / 항목 인덱스가 범위 밖",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "동시 변경 충돌 (재시도 후에도 실패)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun updateItem(
        userId: Long,
        id: Long,
        index: Int,
        request: UpdateRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "회고 항목 삭제",
        description = "인덱스 위치 항목을 제거합니다. 이후 항목들의 인덱스는 -1 씩 당겨집니다. 인덱스가 범위를 벗어나면 404 RETROSPECTIVE_ITEM_INDEX_INVALID 로 응답합니다. 동시 변경 충돌 시 1회 자동 재시도되며, 두 번째도 실패하면 409 RETROSPECTIVE_CONCURRENT_MODIFICATION 으로 응답합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "회고 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "12",
            ),
            Parameter(
                name = "index",
                description = "삭제할 항목의 0-base 인덱스",
                required = true,
                `in` = ParameterIn.PATH,
                example = "0",
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "삭제 성공 (전체 items + version 반환)",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "회고를 찾을 수 없거나 본인 소유가 아님 / 항목 인덱스가 범위 밖",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "동시 변경 충돌 (재시도 후에도 실패)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun deleteItem(
        userId: Long,
        id: Long,
        index: Int,
    ): ApiResponse<RetrospectiveItemsResponse>

    @Operation(
        summary = "AI 회고 질문 생성",
        description = "채용 카드와 선택 단계 컨텍스트를 기반으로 회고 질문 목록을 생성합니다. 결과는 DB에 저장하지 않습니다.",
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RetrospectiveQuestionRequest::class),
                    examples = [
                        ExampleObject(
                            name = "generateRetrospectiveQuestions",
                            value = """{"applicationId":101,"stageId":5}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "질문 생성 성공",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "채용 카드 또는 단계를 찾을 수 없거나 본인 소유가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "429",
        description = "AI 질문 생성 한도 초과",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "502",
        description = "AI 질문 생성 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun generateAiQuestions(
        userId: Long,
        request: RetrospectiveQuestionRequest,
    ): ApiResponse<RetrospectiveQuestionsResponse>

    @Operation(
        summary = "Apply retrospective template",
        description = "Appends the selected template questions to the retrospective items. Answers are initialized as null.",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "Template applied.",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Retrospective or template was not found.",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun applyTemplate(
        userId: Long,
        id: Long,
        request: ApplyRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveResponse>
}

package com.cheerup.demo.application.api

import com.cheerup.demo.application.dto.CreateStageRequest
import com.cheerup.demo.application.dto.StageResponse
import com.cheerup.demo.application.dto.UpdateStageRequest
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.global.response.ErrorResponse
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

@Tag(name = "Stages", description = "칸반 보드 Stage(컬럼) API")
interface StageApi {

    @Operation(
        summary = "Stage 목록 조회",
        description = "사용자가 소유한 Stage를 displayOrder 오름차순으로 반환합니다.",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "조회 성공",
    )
    fun list(userId: Long): ApiResponse<List<StageResponse>>

    @Operation(
        summary = "Stage 생성",
        description = "새 Stage를 생성합니다. category는 항상 IN_PROGRESS로 자동 설정되며 사용자가 지정할 수 없습니다. " +
            "displayOrder를 생략하면 사용자의 마지막 Stage 다음 순서로 자동 부여됩니다.",
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateStageRequest::class),
                    examples = [
                        ExampleObject(
                            name = "createStage",
                            value = """{"name":"코딩 테스트","color":"#F59E0B"}""",
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
    fun create(
        userId: Long,
        request: CreateStageRequest,
    ): ResponseEntity<ApiResponse<StageResponse>>

    @Operation(
        summary = "Stage 수정",
        description = "Stage의 name/color/displayOrder를 부분 수정합니다. category는 시스템 관리 항목이라 변경할 수 없습니다. " +
            "null 필드는 변경하지 않습니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "수정할 Stage ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "2",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UpdateStageRequest::class),
                    examples = [
                        ExampleObject(
                            name = "renameStage",
                            value = """{"name":"1차 면접","displayOrder":3}""",
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
        responseCode = "404",
        description = "Stage를 찾을 수 없거나 본인 소유가 아님",
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
        request: UpdateStageRequest,
    ): ApiResponse<StageResponse>

    @Operation(
        summary = "Stage 삭제",
        description = "Stage를 삭제합니다. (1) category가 PASSED/REJECTED가 아닐 것, (2) 해당 Stage에 카드가 0개일 것을 모두 만족해야 합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "삭제할 Stage ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "2",
            ),
        ],
    )
    @SwaggerApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)")
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Stage를 찾을 수 없거나 본인 소유가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "고정 단계(STAGE_FIXED) 또는 카드 잔존(STAGE_NOT_EMPTY)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    fun delete(userId: Long, id: Long): ResponseEntity<Void>
}

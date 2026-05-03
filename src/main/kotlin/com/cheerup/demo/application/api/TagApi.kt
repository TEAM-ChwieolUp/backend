package com.cheerup.demo.application.api

import com.cheerup.demo.application.dto.CreateTagRequest
import com.cheerup.demo.application.dto.TagResponse
import com.cheerup.demo.application.dto.UpdateTagRequest
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

@Tag(name = "Tags", description = "칸반 카드 태그 API")
interface TagApi {

    @Operation(
        summary = "태그 목록 조회",
        description = "사용자가 소유한 태그를 ID 오름차순으로 반환합니다.",
    )
    @SwaggerApiResponse(responseCode = "200", description = "조회 성공")
    fun list(userId: Long): ApiResponse<List<TagResponse>>

    @Operation(
        summary = "태그 생성",
        description = "새 태그를 생성합니다. 같은 사용자 내에서 name이 중복되면 409 TAG_DUPLICATE를 반환합니다.",
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreateTagRequest::class),
                    examples = [
                        ExampleObject(
                            name = "createTag",
                            value = """{"name":"원격","color":"#0EA5E9"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(responseCode = "201", description = "생성 성공")
    @SwaggerApiResponse(
        responseCode = "400",
        description = "요청 본문 검증 실패",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "이미 존재하는 태그 이름 (TAG_DUPLICATE)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(userId: Long, request: CreateTagRequest): ResponseEntity<ApiResponse<TagResponse>>

    @Operation(
        summary = "태그 수정",
        description = "태그의 name/color를 부분 수정합니다. null 필드는 변경하지 않습니다. 이름 변경으로 동일 사용자 내 중복이 발생하면 409 TAG_DUPLICATE를 반환합니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "수정할 태그 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "5",
            ),
        ],
        requestBody = RequestBody(
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UpdateTagRequest::class),
                    examples = [
                        ExampleObject(
                            name = "renameTag",
                            value = """{"name":"하이브리드","color":"#22C55E"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @SwaggerApiResponse(responseCode = "200", description = "수정 성공")
    @SwaggerApiResponse(
        responseCode = "404",
        description = "태그를 찾을 수 없거나 본인 소유가 아님",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "이름 변경으로 중복 발생 (TAG_DUPLICATE)",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun update(userId: Long, id: Long, request: UpdateTagRequest): ApiResponse<TagResponse>

    @Operation(
        summary = "태그 삭제",
        description = "태그를 hard delete 합니다. 연결된 application_tags 행은 DB FK CASCADE로 자동 정리됩니다.",
        parameters = [
            Parameter(
                name = "id",
                description = "삭제할 태그 ID",
                required = true,
                `in` = ParameterIn.PATH,
                example = "5",
            ),
        ],
    )
    @SwaggerApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)")
    @SwaggerApiResponse(
        responseCode = "404",
        description = "태그를 찾을 수 없거나 본인 소유가 아님",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    fun delete(userId: Long, id: Long): ResponseEntity<Void>
}

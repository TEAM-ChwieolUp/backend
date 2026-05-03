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

@Tag(name = "Tags", description = "칸반 카드 태그 API")
interface TagApi {

    @Operation(
        summary = "태그 목록 조회",
        description = "사용자가 소유한 태그를 ID 오름차순으로 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        ],
    )
    fun list(
        @Parameter(hidden = true) userId: Long,
    ): ApiResponse<List<TagResponse>>

    @Operation(
        summary = "태그 생성",
        description = "새 태그를 생성합니다. 같은 사용자 내에서 name이 중복되면 409 TAG_DUPLICATE를 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.TAG_DUPLICATE),
        ],
    )
    fun create(
        @Parameter(hidden = true) userId: Long,
        request: CreateTagRequest,
    ): ResponseEntity<ApiResponse<TagResponse>>

    @Operation(
        summary = "태그 수정",
        description = "태그의 name/color를 부분 수정합니다. null 필드는 변경하지 않습니다. " +
            "이름 변경으로 동일 사용자 내 중복이 발생하면 409 TAG_DUPLICATE를 반환합니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.INVALID_INPUT),
            SwaggerErrorResponse(ErrorCode.TAG_NOT_FOUND),
            SwaggerErrorResponse(ErrorCode.TAG_DUPLICATE),
        ],
    )
    fun update(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "수정할 태그 ID", example = "5") id: Long,
        request: UpdateTagRequest,
    ): ApiResponse<TagResponse>

    @Operation(
        summary = "태그 삭제",
        description = "태그를 hard delete 합니다. 연결된 application_tags 행은 DB FK CASCADE로 자동 정리됩니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @SwaggerErrorResponses(
        errors = [
            SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
            SwaggerErrorResponse(ErrorCode.TAG_NOT_FOUND),
        ],
    )
    fun delete(
        @Parameter(hidden = true) userId: Long,
        @Parameter(description = "삭제할 태그 ID", example = "5") id: Long,
    ): ResponseEntity<Void>
}

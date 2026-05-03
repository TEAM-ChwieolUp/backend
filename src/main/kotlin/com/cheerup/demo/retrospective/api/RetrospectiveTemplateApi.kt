package com.cheerup.demo.retrospective.api

import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveTemplateResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveTemplateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Retrospective Templates", description = "Retrospective question template API")
interface RetrospectiveTemplateApi {

    @Operation(summary = "List retrospective templates")
    fun list(userId: Long): ApiResponse<List<RetrospectiveTemplateResponse>>

    @Operation(summary = "Get retrospective template")
    fun get(userId: Long, id: Long): ApiResponse<RetrospectiveTemplateResponse>

    @Operation(summary = "Create retrospective template")
    fun create(
        userId: Long,
        request: CreateRetrospectiveTemplateRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveTemplateResponse>>

    @Operation(summary = "Update retrospective template")
    fun update(
        userId: Long,
        id: Long,
        request: UpdateRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveTemplateResponse>

    @Operation(summary = "Delete retrospective template")
    fun delete(userId: Long, id: Long): ResponseEntity<Void>
}

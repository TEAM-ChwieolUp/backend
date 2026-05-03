package com.cheerup.demo.retrospective.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.api.RetrospectiveApi
import com.cheerup.demo.retrospective.dto.AddRetrospectiveItemRequest
import com.cheerup.demo.retrospective.dto.ApplyRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveItemsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveListResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveQuestionsResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveItemRequest
import com.cheerup.demo.retrospective.service.RetrospectiveCommandService
import com.cheerup.demo.retrospective.service.RetrospectiveQueryService
import com.cheerup.demo.retrospective.service.RetrospectiveQuestionService
import com.cheerup.demo.retrospective.service.RetrospectiveTemplateService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RetrospectiveController(
    private val retrospectiveQueryService: RetrospectiveQueryService,
    private val retrospectiveCommandService: RetrospectiveCommandService,
    private val retrospectiveQuestionService: RetrospectiveQuestionService,
    private val retrospectiveTemplateService: RetrospectiveTemplateService,
) : RetrospectiveApi {

    @AssignUserId
    @GetMapping("/api/applications/{appId}/retrospectives")
    override fun listByApplication(
        userId: Long,
        @PathVariable appId: Long,
    ): ApiResponse<RetrospectiveListResponse> =
        ApiResponse.success(retrospectiveQueryService.getByApplication(userId, appId))

    @AssignUserId
    @GetMapping("/api/retrospectives/{id}")
    override fun getOne(
        userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<RetrospectiveResponse> =
        ApiResponse.success(retrospectiveQueryService.getOne(userId, id))

    @AssignUserId
    @DeleteMapping("/api/retrospectives/{id}")
    override fun delete(
        userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        retrospectiveCommandService.delete(userId, id)
        return ResponseEntity.noContent().build()
    }

    @AssignUserId
    @PostMapping("/api/retrospectives/{id}/items")
    override fun addItem(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: AddRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse> =
        ApiResponse.success(retrospectiveCommandService.addItem(userId, id, request))

    @AssignUserId
    @PatchMapping("/api/retrospectives/{id}/items/{index}")
    override fun updateItem(
        userId: Long,
        @PathVariable id: Long,
        @PathVariable index: Int,
        @Valid @RequestBody request: UpdateRetrospectiveItemRequest,
    ): ApiResponse<RetrospectiveItemsResponse> =
        ApiResponse.success(retrospectiveCommandService.updateItem(userId, id, index, request))

    @AssignUserId
    @DeleteMapping("/api/retrospectives/{id}/items/{index}")
    override fun deleteItem(
        userId: Long,
        @PathVariable id: Long,
        @PathVariable index: Int,
    ): ApiResponse<RetrospectiveItemsResponse> =
        ApiResponse.success(retrospectiveCommandService.removeItem(userId, id, index))

    @AssignUserId
    @PostMapping("/api/retrospectives/ai-questions")
    override fun generateAiQuestions(
        userId: Long,
        @Valid @RequestBody request: RetrospectiveQuestionRequest,
    ): ApiResponse<RetrospectiveQuestionsResponse> =
        ApiResponse.success(retrospectiveQuestionService.generateQuestions(userId, request))

    @AssignUserId
    @PostMapping("/api/retrospectives/{id}/apply-template")
    override fun applyTemplate(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ApplyRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveResponse> =
        ApiResponse.success(retrospectiveTemplateService.applyTemplate(userId, id, request))
}

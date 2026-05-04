package com.cheerup.demo.retrospective.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.api.RetrospectiveTemplateApi
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveTemplateResponse
import com.cheerup.demo.retrospective.dto.UpdateRetrospectiveTemplateRequest
import com.cheerup.demo.retrospective.service.RetrospectiveTemplateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/retrospective-templates")
class RetrospectiveTemplateController(
    private val retrospectiveTemplateService: RetrospectiveTemplateService,
) : RetrospectiveTemplateApi {

    @AssignUserId
    @GetMapping
    override fun list(
        userId: Long,
    ): ApiResponse<List<RetrospectiveTemplateResponse>> =
        ApiResponse.success(retrospectiveTemplateService.list(userId))

    @AssignUserId
    @GetMapping("/{id}")
    override fun get(
        userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<RetrospectiveTemplateResponse> =
        ApiResponse.success(retrospectiveTemplateService.get(userId, id))

    @AssignUserId
    @PostMapping
    override fun create(
        userId: Long,
        @Valid @RequestBody request: CreateRetrospectiveTemplateRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveTemplateResponse>> {
        val created = retrospectiveTemplateService.create(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created))
    }

    @AssignUserId
    @PatchMapping("/{id}")
    override fun update(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRetrospectiveTemplateRequest,
    ): ApiResponse<RetrospectiveTemplateResponse> =
        ApiResponse.success(retrospectiveTemplateService.update(userId, id, request))

    @AssignUserId
    @DeleteMapping("/{id}")
    override fun delete(
        userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        retrospectiveTemplateService.delete(userId, id)
        return ResponseEntity.noContent().build()
    }
}

package com.cheerup.demo.application.controller

import com.cheerup.demo.application.api.ApplicationApi
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.dto.ApplicationCard
import com.cheerup.demo.application.dto.ApplicationResponse
import com.cheerup.demo.application.dto.BoardResponse
import com.cheerup.demo.application.dto.CreateApplicationRequest
import com.cheerup.demo.application.dto.UpdateApplicationRequest
import com.cheerup.demo.application.service.ApplicationService
import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.retrospective.dto.CreateRetrospectiveRequest
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.service.RetrospectiveCommandService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/applications")
class ApplicationController(
    private val applicationService: ApplicationService,
    private val retrospectiveCommandService: RetrospectiveCommandService,
) : ApplicationApi {

    @AssignUserId
    @GetMapping
    override fun getBoard(
        userId: Long,
        @RequestParam(required = false) stage: Long?,
        @RequestParam(required = false) tag: Long?,
        @RequestParam(required = false) priority: Priority?,
    ): ApiResponse<BoardResponse> =
        ApiResponse.success(applicationService.getBoard(userId, stage, tag, priority))

    @AssignUserId
    @PostMapping
    override fun createApplication(
        userId: Long,
        @Valid @RequestBody request: CreateApplicationRequest,
    ): ResponseEntity<ApiResponse<ApplicationCard>> {
        val created = applicationService.createApplication(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created))
    }

    @AssignUserId
    @PatchMapping("/{id}")
    override fun update(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateApplicationRequest,
    ): ApiResponse<ApplicationResponse> =
        ApiResponse.success(applicationService.update(userId, id, request))

    @AssignUserId
    @DeleteMapping("/{id}")
    override fun delete(
        userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        applicationService.deleteApplication(userId, id)
        return ResponseEntity.noContent().build()
    }

    @AssignUserId
    @PostMapping("/{id}/retrospectives")
    override fun createRetrospective(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateRetrospectiveRequest,
    ): ResponseEntity<ApiResponse<RetrospectiveResponse>> {
        val created = retrospectiveCommandService.create(userId, id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created))
    }
}

package com.cheerup.demo.application.controller

import com.cheerup.demo.application.api.StageApi
import com.cheerup.demo.application.dto.CreateStageRequest
import com.cheerup.demo.application.dto.StageResponse
import com.cheerup.demo.application.dto.UpdateStageRequest
import com.cheerup.demo.application.service.StageService
import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
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
@RequestMapping("/api/stages")
class StageController(
    private val stageService: StageService,
) : StageApi {

    @AssignUserId
    @GetMapping
    override fun list(
        userId: Long,
    ): ApiResponse<List<StageResponse>> =
        ApiResponse.success(stageService.list(userId))

    @AssignUserId
    @PostMapping
    override fun create(
        userId: Long,
        @Valid @RequestBody request: CreateStageRequest,
    ): ResponseEntity<ApiResponse<StageResponse>> {
        val created = stageService.create(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created))
    }

    @AssignUserId
    @PatchMapping("/{id}")
    override fun update(
        userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStageRequest,
    ): ApiResponse<StageResponse> =
        ApiResponse.success(stageService.update(userId, id, request))

    @AssignUserId
    @DeleteMapping("/{id}")
    override fun delete(
        userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        stageService.delete(userId, id)
        return ResponseEntity.noContent().build()
    }
}

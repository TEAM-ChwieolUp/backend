package com.cheerup.demo.mail.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.mail.api.MailApi
import com.cheerup.demo.mail.dto.ClassifiedMailMessagesResponse
import com.cheerup.demo.mail.domain.MailSuggestionStatus
import com.cheerup.demo.mail.dto.AnalyzeMailSuggestionRequest
import com.cheerup.demo.mail.dto.MailSuggestionResponse
import com.cheerup.demo.mail.dto.MailSuggestionsResponse
import com.cheerup.demo.mail.service.MailQueryService
import com.cheerup.demo.mail.service.MailSuggestionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailController(
    private val mailQueryService: MailQueryService,
    private val mailSuggestionService: MailSuggestionService,
) : MailApi {

    @AssignUserId
    @GetMapping("/messages/classified")
    override fun listClassifiedMessages(
        userId: Long,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<ClassifiedMailMessagesResponse> =
        ApiResponse.success(
            mailQueryService.listClassifiedMessages(
                userId = userId,
                limit = limit,
            ),
        )

    @AssignUserId
    @PostMapping("/suggestions/analyze")
    override fun analyzeSuggestion(
        userId: Long,
        @Valid @RequestBody request: AnalyzeMailSuggestionRequest,
    ): ApiResponse<MailSuggestionResponse> =
        ApiResponse.success(mailSuggestionService.analyze(userId, request))

    @AssignUserId
    @GetMapping("/suggestions")
    override fun listSuggestions(
        userId: Long,
        @RequestParam(defaultValue = "PENDING") status: MailSuggestionStatus,
    ): ApiResponse<MailSuggestionsResponse> =
        ApiResponse.success(mailSuggestionService.list(userId, status))

    @AssignUserId
    @PostMapping("/suggestions/{id}/accept")
    override fun acceptSuggestion(
        userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<MailSuggestionResponse> =
        ApiResponse.success(mailSuggestionService.accept(userId, id))

    @AssignUserId
    @PostMapping("/suggestions/{id}/reject")
    override fun rejectSuggestion(
        userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<MailSuggestionResponse> =
        ApiResponse.success(mailSuggestionService.reject(userId, id))
}

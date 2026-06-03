package com.cheerup.demo.mail.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.mail.api.MailApi
import com.cheerup.demo.mail.dto.ClassifiedMailMessagesResponse
import com.cheerup.demo.mail.service.MailQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailController(
    private val mailQueryService: MailQueryService,
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
}

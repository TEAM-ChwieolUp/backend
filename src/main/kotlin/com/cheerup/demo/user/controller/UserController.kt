package com.cheerup.demo.user.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.user.dto.MeResponse
import com.cheerup.demo.user.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @AssignUserId
    @GetMapping("/me")
    fun me(userId: Long): ApiResponse<MeResponse> {
        val user = userService.getById(userId)

        return ApiResponse.success(
            MeResponse(
                id = requireNotNull(user.id),
                oauth2Provider = user.oauth2Provider,
                email = user.email,
                name = user.name,
                profileImageUrl = user.profileImageUrl,
            ),
        )
    }
}

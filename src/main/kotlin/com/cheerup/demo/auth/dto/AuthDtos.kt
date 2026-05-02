package com.cheerup.demo.auth.dto

data class UserSummary(
    val id: Long,
    val email: String,
    val name: String?,
    val profileImageUrl: String?,
)

data class LoginResponse(
    val accessToken: String,
    val user: UserSummary,
)

data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
    val refreshTokenMaxAgeSeconds: Long,
    val user: UserSummary,
)

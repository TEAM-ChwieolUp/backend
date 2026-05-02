package com.cheerup.demo.user.dto

import com.cheerup.demo.user.domain.OAuth2Provider

data class MeResponse(
    val id: Long,
    val oauth2Provider: OAuth2Provider,
    val email: String,
    val name: String?,
    val profileImageUrl: String?,
)

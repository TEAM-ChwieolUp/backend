package com.cheerup.demo.global.oauth

import com.cheerup.demo.user.domain.OAuth2Provider

interface OAuth2UserInfo {
    val provider: OAuth2Provider
    val providerUserId: String
    val email: String
    val name: String?
    val profileImageUrl: String?
}

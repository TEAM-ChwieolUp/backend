package com.cheerup.demo.user.repository

import com.cheerup.demo.user.domain.OAuth2Provider
import com.cheerup.demo.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByOauth2ProviderAndProviderUserId(
        oauth2Provider: OAuth2Provider,
        providerUserId: String,
    ): User?
}

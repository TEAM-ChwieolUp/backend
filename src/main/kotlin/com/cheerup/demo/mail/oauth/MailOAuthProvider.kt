package com.cheerup.demo.mail.oauth

import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant

interface MailOAuthProvider {
    fun supports(provider: MailProvider): Boolean

    fun buildAuthorizationUrl(command: MailOAuthAuthorizeCommand): String

    fun exchangeCode(command: MailOAuthCallbackCommand): MailOAuthToken

    fun fetchAccount(token: MailOAuthToken): MailOAuthAccount
}

data class MailOAuthAuthorizeCommand(
    val state: String,
)

data class MailOAuthCallbackCommand(
    val code: String,
)

data class MailOAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant?,
    val scope: String?,
)

data class MailOAuthAccount(
    val providerAccountId: String,
    val email: String,
    val displayName: String?,
)

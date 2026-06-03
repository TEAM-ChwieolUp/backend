package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailIntegration
import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant

data class MailIntegrationContext(
    val integrationId: Long?,
    val userId: Long,
    val provider: MailProvider,
    val providerAccountId: String,
    val email: String,
    val displayName: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAt: Instant?,
    val scope: String?,
)

fun MailIntegration.toContext(): MailIntegrationContext =
    MailIntegrationContext(
        integrationId = id,
        userId = userId,
        provider = provider,
        providerAccountId = providerAccountId,
        email = email,
        displayName = displayName,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        scope = scope,
    )

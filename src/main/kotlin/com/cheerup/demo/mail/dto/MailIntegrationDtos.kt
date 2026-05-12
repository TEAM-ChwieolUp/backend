package com.cheerup.demo.mail.dto

import com.cheerup.demo.mail.domain.MailIntegration
import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant

data class MailIntegrationResponse(
    val id: Long,
    val provider: MailProvider,
    val email: String,
    val displayName: String?,
    val connectedAt: Instant,
    val lastSyncedAt: Instant?,
    val active: Boolean,
)

data class MailIntegrationConnectedResponse(
    val integrationId: Long,
    val provider: MailProvider,
    val email: String,
)

data class MailOAuthAuthorizeResponse(
    val authorizationUrl: String,
)

fun MailIntegration.toResponse(): MailIntegrationResponse =
    MailIntegrationResponse(
        id = requireNotNull(id) { "MailIntegration must be persisted" },
        provider = provider,
        email = email,
        displayName = displayName,
        connectedAt = connectedAt,
        lastSyncedAt = lastSyncedAt,
        active = active,
    )

fun MailIntegration.toConnectedResponse(): MailIntegrationConnectedResponse =
    MailIntegrationConnectedResponse(
        integrationId = requireNotNull(id) { "MailIntegration must be persisted" },
        provider = provider,
        email = email,
    )

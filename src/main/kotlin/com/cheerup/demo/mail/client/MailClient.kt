package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant

interface MailClient {
    fun supports(provider: MailProvider): Boolean

    fun listMessages(integration: MailIntegrationContext, limit: Int): List<MailMessageCandidate>

    fun getMessageContent(integration: MailIntegrationContext, messageId: String): MailMessageContent
}

data class MailMessageCandidate(
    val integrationId: Long?,
    val provider: MailProvider,
    val accountEmail: String,
    val messageId: String,
    val threadId: String,
    val subject: String,
    val from: String,
    val receivedAt: Instant,
    val snippet: String,
)

data class MailMessageContent(
    val subject: String,
    val body: String,
)

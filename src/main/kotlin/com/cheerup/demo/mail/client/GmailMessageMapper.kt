package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GmailMessageMapper {

    fun toCandidate(
        integration: MailIntegrationContext,
        message: GmailMessageResponse,
    ): MailMessageCandidate {
        val headers = message.payload?.headers.orEmpty()
            .associate { it.name.lowercase() to it.value }

        return MailMessageCandidate(
            integrationId = integration.integrationId,
            provider = MailProvider.GOOGLE,
            accountEmail = integration.email,
            messageId = message.id,
            threadId = message.threadId,
            subject = headers["subject"].orEmpty(),
            from = headers["from"].orEmpty(),
            receivedAt = parseReceivedAt(
                dateHeader = headers["date"],
                internalDate = message.internalDate,
            ),
            snippet = message.snippet.orEmpty(),
        )
    }

    private fun parseReceivedAt(
        dateHeader: String?,
        internalDate: Long?,
    ): Instant {
        if (!dateHeader.isNullOrBlank()) {
            runCatching {
                return ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            }
        }

        if (internalDate != null) {
            return Instant.ofEpochMilli(internalDate)
        }

        return Instant.EPOCH
    }
}

data class GmailMessageListResponse(
    val messages: List<GmailMessageSummary> = emptyList(),
)

data class GmailMessageSummary(
    val id: String,
    val threadId: String,
)

data class GmailMessageResponse(
    val id: String,
    val threadId: String,
    val snippet: String? = null,
    val internalDate: Long? = null,
    val payload: GmailPayload? = null,
)

data class GmailPayload(
    val headers: List<GmailHeader> = emptyList(),
)

data class GmailHeader(
    val name: String,
    val value: String,
)

package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import org.springframework.web.util.HtmlUtils

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

    fun toContent(message: GmailMessageResponse): MailMessageContent {
        val headers = message.payload?.headers.orEmpty()
            .associate { it.name.lowercase() to it.value }
        val body = extractBody(message.payload)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Gmail message body is empty")

        return MailMessageContent(
            subject = headers["subject"].orEmpty(),
            body = body,
        )
    }

    private fun extractBody(payload: GmailPayload?): String? {
        if (payload == null) return null

        findPart(payload, "text/plain")?.let { return decode(it.body?.data) }
        findPart(payload, "text/html")?.let { return htmlToText(decode(it.body?.data)) }

        val direct = decode(payload.body?.data)
        return if (payload.mimeType.equals("text/html", ignoreCase = true)) htmlToText(direct) else direct
    }

    private fun findPart(payload: GmailPayload, mimeType: String): GmailPayload? {
        if (payload.mimeType.equals(mimeType, ignoreCase = true) && !payload.body?.data.isNullOrBlank()) {
            return payload
        }
        return payload.parts.orEmpty().firstNotNullOfOrNull { findPart(it, mimeType) }
    }

    private fun decode(data: String?): String? {
        if (data.isNullOrBlank()) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(data), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun htmlToText(html: String?): String? {
        if (html == null) return null
        val withoutTags = html
            .replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|tr|h[1-6])>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), " ")
        return HtmlUtils.htmlUnescape(withoutTags)
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n")
            .trim()
    }

    private fun parseReceivedAt(
        dateHeader: String?,
        internalDate: String?,
    ): Instant {
        if (!dateHeader.isNullOrBlank()) {
            runCatching {
                return ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            }
        }

        internalDate?.toLongOrNull()?.let {
            return Instant.ofEpochMilli(it)
        }

        return Instant.EPOCH
    }
}

data class GmailMessageListResponse(
    val messages: List<GmailMessageSummary>? = emptyList(),
)

data class GmailMessageSummary(
    val id: String,
    val threadId: String,
)

data class GmailMessageResponse(
    val id: String,
    val threadId: String,
    val snippet: String? = null,
    val internalDate: String? = null,
    val payload: GmailPayload? = null,
)

data class GmailPayload(
    val headers: List<GmailHeader>? = emptyList(),
    val mimeType: String? = null,
    val body: GmailBody? = null,
    val parts: List<GmailPayload>? = emptyList(),
)

data class GmailBody(
    val data: String? = null,
)

data class GmailHeader(
    val name: String,
    val value: String,
)

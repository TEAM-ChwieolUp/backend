package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class GmailMessageMapperTest {
    private val mapper = GmailMessageMapper()

    @Test
    fun `maps Gmail metadata response to mail candidate`() {
        val candidate = mapper.toCandidate(
            integration = integrationContext(),
            message = GmailMessageResponse(
                id = "message-1",
                threadId = "thread-1",
                snippet = "snippet",
                internalDate = 1_762_000_000_000,
                payload = GmailPayload(
                    headers = listOf(
                        GmailHeader("Subject", "서류 결과 안내"),
                        GmailHeader("From", "recruit@example.com"),
                        GmailHeader("Date", "Mon, 11 May 2026 10:00:00 +0900"),
                    ),
                ),
            ),
        )

        assertEquals(1L, candidate.integrationId)
        assertEquals(MailProvider.GOOGLE, candidate.provider)
        assertEquals("user@example.com", candidate.accountEmail)
        assertEquals("message-1", candidate.messageId)
        assertEquals("thread-1", candidate.threadId)
        assertEquals("서류 결과 안내", candidate.subject)
        assertEquals("recruit@example.com", candidate.from)
        assertEquals(Instant.parse("2026-05-11T01:00:00Z"), candidate.receivedAt)
        assertEquals("snippet", candidate.snippet)
    }

    @Test
    fun `uses internal date when date header is missing`() {
        val candidate = mapper.toCandidate(
            integration = integrationContext(),
            message = GmailMessageResponse(
                id = "message-1",
                threadId = "thread-1",
                internalDate = 1_762_000_000_000,
                payload = GmailPayload(
                    headers = listOf(
                        GmailHeader("Subject", "면접 일정 안내"),
                    ),
                ),
            ),
        )

        assertEquals(Instant.ofEpochMilli(1_762_000_000_000), candidate.receivedAt)
        assertEquals("면접 일정 안내", candidate.subject)
        assertEquals("", candidate.from)
        assertEquals("", candidate.snippet)
    }

    private fun integrationContext(): MailIntegrationContext =
        MailIntegrationContext(
            integrationId = 1L,
            userId = 10L,
            provider = MailProvider.GOOGLE,
            providerAccountId = "google-account",
            email = "user@example.com",
            displayName = "User",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAt = Instant.parse("2026-05-12T00:00:00Z"),
            scope = "gmail.readonly",
        )
}

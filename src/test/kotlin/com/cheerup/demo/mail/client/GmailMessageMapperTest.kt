package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import tools.jackson.databind.json.JsonMapper

class GmailMessageMapperTest {
    private val mapper = GmailMessageMapper()
    private val objectMapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `maps Gmail metadata response to mail candidate`() {
        val candidate = mapper.toCandidate(
            integration = integrationContext(),
            message = GmailMessageResponse(
                id = "message-1",
                threadId = "thread-1",
                snippet = "snippet",
                internalDate = "1762000000000",
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
                internalDate = "1762000000000",
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

    @Test
    fun `deserializes Gmail internal date string and maps received at`() {
        val message = objectMapper.readValue(
            """
            {
              "id": "message-1",
              "threadId": "thread-1",
              "internalDate": "1762000000000",
              "payload": {
                "headers": [
                  {"name": "Subject", "value": "면접 일정 안내"}
                ]
              }
            }
            """.trimIndent(),
            GmailMessageResponse::class.java,
        )

        val candidate = mapper.toCandidate(integrationContext(), message)

        assertEquals("1762000000000", message.internalDate)
        assertEquals(Instant.ofEpochMilli(1_762_000_000_000), candidate.receivedAt)
    }

    @Test
    fun `deserializes explicit null collections from Gmail response`() {
        val message = objectMapper.readValue(
            """
            {
              "id": "message-1",
              "threadId": "thread-1",
              "payload": {
                "headers": null,
                "parts": null,
                "body": null
              }
            }
            """.trimIndent(),
            GmailMessageResponse::class.java,
        )

        val candidate = mapper.toCandidate(integrationContext(), message)

        assertEquals("", candidate.subject)
        assertEquals("", candidate.from)
    }

    @Test
    fun `uses epoch when internal date is invalid`() {
        val candidate = mapper.toCandidate(
            integration = integrationContext(),
            message = GmailMessageResponse(
                id = "message-1",
                threadId = "thread-1",
                internalDate = "invalid",
            ),
        )

        assertEquals(Instant.EPOCH, candidate.receivedAt)
    }

    @Test
    fun `extracts plain text from nested multipart payload`() {
        val content = mapper.toContent(
            GmailMessageResponse(
                id = "message-1",
                threadId = "thread-1",
                payload = GmailPayload(
                    headers = listOf(GmailHeader("Subject", "1차 면접 안내")),
                    mimeType = "multipart/mixed",
                    parts = listOf(
                        GmailPayload(
                            mimeType = "multipart/alternative",
                            parts = listOf(
                                GmailPayload(
                                    mimeType = "text/html",
                                    body = GmailBody(encoded("<p>HTML body</p>")),
                                ),
                                GmailPayload(
                                    mimeType = "text/plain",
                                    body = GmailBody(encoded("기술 면접 일정을 안내드립니다.")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("1차 면접 안내", content.subject)
        assertEquals("기술 면접 일정을 안내드립니다.", content.body)
    }

    @Test
    fun `falls back to normalized html text`() {
        val content = mapper.toContent(
            GmailMessageResponse(
                id = "message-1",
                threadId = "thread-1",
                payload = GmailPayload(
                    mimeType = "text/html",
                    body = GmailBody(encoded("<div>최종 면접<br>일정 안내 &amp; 확인</div>")),
                ),
            ),
        )

        assertEquals("최종 면접\n일정 안내 & 확인", content.body)
    }

    private fun encoded(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

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

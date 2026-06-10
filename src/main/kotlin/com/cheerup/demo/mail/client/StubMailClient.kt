package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(
    prefix = "app.mail.clients",
    name = ["google"],
    havingValue = "stub",
    matchIfMissing = true,
)
class StubGoogleMailClient : MailClient {

    override fun supports(provider: MailProvider): Boolean =
        provider == MailProvider.GOOGLE

    override fun listMessages(integration: MailIntegrationContext, limit: Int): List<MailMessageCandidate> {
        return sampleMessages(integration).take(limit.coerceIn(1, MAX_LIMIT))
    }

    override fun getMessageContent(
        integration: MailIntegrationContext,
        messageId: String,
    ): MailMessageContent {
        val message = sampleMessages(integration).firstOrNull { it.messageId == messageId }
            ?: throw IllegalArgumentException("Unknown stub messageId=$messageId")
        return MailMessageContent(subject = message.subject, body = message.snippet)
    }

    companion object {
        private const val MAX_LIMIT = 50

        private fun sampleMessages(integration: MailIntegrationContext): List<MailMessageCandidate> =
            listOf(
                MailMessageCandidate(
                    integrationId = integration.integrationId,
                    provider = integration.provider,
                    accountEmail = integration.email,
                    messageId = "stub-google-message-1",
                    threadId = "stub-google-thread-1",
                    subject = "[카카오] 코딩테스트 안내",
                    from = "recruit@kakao.com",
                    receivedAt = Instant.parse("2026-05-06T01:00:00Z"),
                    snippet = "백엔드 개발자 포지션 코딩테스트 일정을 안내드립니다.",
                ),
                MailMessageCandidate(
                    integrationId = integration.integrationId,
                    provider = integration.provider,
                    accountEmail = integration.email,
                    messageId = "stub-google-message-2",
                    threadId = "stub-google-thread-2",
                    subject = "[네이버] 서류 전형 결과 안내",
                    from = "career@naver.com",
                    receivedAt = Instant.parse("2026-05-05T02:00:00Z"),
                    snippet = "지원하신 포지션의 서류 전형 결과를 안내드립니다.",
                ),
                MailMessageCandidate(
                    integrationId = integration.integrationId,
                    provider = integration.provider,
                    accountEmail = integration.email,
                    messageId = "stub-google-message-3",
                    threadId = "stub-google-thread-3",
                    subject = "[라인] 1차 면접 일정 조율",
                    from = "recruit@linecorp.com",
                    receivedAt = Instant.parse("2026-05-04T03:00:00Z"),
                    snippet = "1차 면접 가능 일정을 회신 부탁드립니다.",
                ),
                MailMessageCandidate(
                    integrationId = integration.integrationId,
                    provider = integration.provider,
                    accountEmail = integration.email,
                    messageId = "stub-google-message-4",
                    threadId = "stub-google-thread-4",
                    subject = "[토스] 최종 합격 안내",
                    from = "recruit@toss.im",
                    receivedAt = Instant.parse("2026-05-03T04:00:00Z"),
                    snippet = "최종 합격을 진심으로 축하드립니다.",
                ),
                MailMessageCandidate(
                    integrationId = integration.integrationId,
                    provider = integration.provider,
                    accountEmail = integration.email,
                    messageId = "stub-google-message-5",
                    threadId = "stub-google-thread-5",
                    subject = "[쿠팡] 전형 결과 안내",
                    from = "recruit@coupang.com",
                    receivedAt = Instant.parse("2026-05-02T05:00:00Z"),
                    snippet = "아쉽게도 이번 채용 전형에서는 함께하기 어렵게 되었습니다.",
                ),
                MailMessageCandidate(
                    integrationId = integration.integrationId,
                    provider = integration.provider,
                    accountEmail = integration.email,
                    messageId = "stub-google-message-6",
                    threadId = "stub-google-thread-6",
                    subject = "주간 뉴스레터",
                    from = "newsletter@example.com",
                    receivedAt = Instant.parse("2026-05-01T06:00:00Z"),
                    snippet = "이번 주 기술 뉴스와 아티클을 모았습니다.",
                ),
            )
    }
}

package com.cheerup.demo.mail.client

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.domain.MailProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "app.mail.clients",
    name = ["outlook"],
    havingValue = "outlook",
)
class OutlookMailClient : MailClient {

    override fun supports(provider: MailProvider): Boolean =
        provider == MailProvider.OUTLOOK

    override fun listMessages(integration: MailIntegrationContext, limit: Int): List<MailMessageCandidate> {
        throw BusinessException(
            ErrorCode.MAIL_CLIENT_NOT_CONFIGURED,
            detail = "Outlook MailClient is not implemented yet.",
        )
    }
}

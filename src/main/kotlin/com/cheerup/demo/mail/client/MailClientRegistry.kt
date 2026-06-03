package com.cheerup.demo.mail.client

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.domain.MailProvider
import org.springframework.stereotype.Component

@Component
class MailClientRegistry(
    mailClients: List<MailClient>,
) {
    private val clientsByProvider: Map<MailProvider, MailClient> =
        MailProvider.entries.associateWith { provider ->
            mailClients.filter { it.supports(provider) }
        }.mapValues { (provider, clients) ->
            when (clients.size) {
                1 -> clients.single()
                0 -> null
                else -> throw IllegalStateException("Multiple MailClient beans support provider=$provider")
            }
        }.filterValues { it != null }
            .mapValues { requireNotNull(it.value) }

    fun get(provider: MailProvider): MailClient =
        clientsByProvider[provider]
            ?: throw BusinessException(
                ErrorCode.MAIL_CLIENT_NOT_CONFIGURED,
                detail = "provider=$provider",
            )
}

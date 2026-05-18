package com.cheerup.demo.mail.oauth

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.domain.MailProvider
import org.springframework.stereotype.Component

@Component
class MailOAuthProviderRegistry(
    providers: List<MailOAuthProvider>,
) {
    private val providerByType: Map<MailProvider, MailOAuthProvider> =
        MailProvider.entries.mapNotNull { provider ->
            val matched = providers.filter { it.supports(provider) }
            if (matched.size > 1) {
                throw IllegalStateException("Multiple mail OAuth providers support $provider")
            }
            matched.singleOrNull()?.let { provider to it }
        }.toMap()

    fun get(provider: MailProvider): MailOAuthProvider =
        providerByType[provider]
            ?: throw BusinessException(
                ErrorCode.MAIL_OAUTH_PROVIDER_NOT_SUPPORTED,
                detail = "provider=$provider",
            )
}

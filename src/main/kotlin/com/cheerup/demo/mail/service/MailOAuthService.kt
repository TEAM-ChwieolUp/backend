package com.cheerup.demo.mail.service

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.domain.MailIntegration
import com.cheerup.demo.mail.domain.MailOAuthState
import com.cheerup.demo.mail.domain.MailProvider
import com.cheerup.demo.mail.dto.MailIntegrationConnectedResponse
import com.cheerup.demo.mail.dto.MailIntegrationResponse
import com.cheerup.demo.mail.dto.toConnectedResponse
import com.cheerup.demo.mail.dto.toResponse
import com.cheerup.demo.mail.oauth.MailOAuthAuthorizeCommand
import com.cheerup.demo.mail.oauth.MailOAuthCallbackCommand
import com.cheerup.demo.mail.oauth.MailOAuthProperties
import com.cheerup.demo.mail.oauth.MailOAuthProviderRegistry
import com.cheerup.demo.mail.oauth.MailTokenCipher
import com.cheerup.demo.mail.repository.MailIntegrationRepository
import com.cheerup.demo.mail.repository.MailOAuthStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class MailOAuthService(
    private val mailOAuthProviderRegistry: MailOAuthProviderRegistry,
    private val mailOAuthStateRepository: MailOAuthStateRepository,
    private val mailIntegrationRepository: MailIntegrationRepository,
    private val properties: MailOAuthProperties,
    private val mailTokenCipher: MailTokenCipher,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun buildAuthorizationUrl(
        userId: Long,
        provider: MailProvider,
        redirectAfter: String?,
    ): String {
        val mailOAuthProvider = mailOAuthProviderRegistry.get(provider)
        val state = generateState()

        mailOAuthStateRepository.save(
            MailOAuthState(
                state = state,
                userId = userId,
                provider = provider,
                redirectAfter = redirectAfter,
                expiresAt = Instant.now().plusSeconds(properties.stateTtlMinutes * 60),
            ),
        )

        return mailOAuthProvider.buildAuthorizationUrl(
            MailOAuthAuthorizeCommand(state = state),
        )
    }

    @Transactional
    fun connectByAuthorizationCode(
        provider: MailProvider,
        code: String,
        stateValue: String,
    ): MailOAuthConnectionResult {
        val state = mailOAuthStateRepository.findByState(stateValue)
            ?: throw BusinessException(ErrorCode.MAIL_OAUTH_STATE_INVALID)
        val now = Instant.now()

        if (state.provider != provider || state.consumedAt != null) {
            throw BusinessException(ErrorCode.MAIL_OAUTH_STATE_INVALID)
        }
        if (state.isExpired(now)) {
            throw BusinessException(ErrorCode.MAIL_OAUTH_STATE_EXPIRED)
        }

        state.consume(now)

        val mailOAuthProvider = mailOAuthProviderRegistry.get(provider)
        val token = mailOAuthProvider.exchangeCode(MailOAuthCallbackCommand(code = code))
        val account = mailOAuthProvider.fetchAccount(token)

        val integration = mailIntegrationRepository.findByUserIdAndProviderAndProviderAccountId(
            userId = state.userId,
            provider = provider,
            providerAccountId = account.providerAccountId,
        )?.apply {
            reconnect(
                email = account.email,
                displayName = account.displayName,
                accessToken = mailTokenCipher.encrypt(token.accessToken),
                refreshToken = mailTokenCipher.encrypt(token.refreshToken),
                expiresAt = token.expiresAt,
                scope = token.scope,
            )
        } ?: MailIntegration(
            userId = state.userId,
            provider = provider,
            providerAccountId = account.providerAccountId,
            email = account.email,
            displayName = account.displayName,
            accessToken = mailTokenCipher.encrypt(token.accessToken),
            refreshToken = mailTokenCipher.encrypt(token.refreshToken),
            expiresAt = token.expiresAt,
            scope = token.scope,
        )

        return MailOAuthConnectionResult(
            connected = mailIntegrationRepository.save(integration).toConnectedResponse(),
            redirectAfter = state.redirectAfter,
        )
    }

    @Transactional(readOnly = true)
    fun listIntegrations(userId: Long): List<MailIntegrationResponse> =
        mailIntegrationRepository.findAllByUserIdAndActiveTrueOrderByIdAsc(userId)
            .map { it.toResponse() }

    @Transactional
    fun disconnect(
        userId: Long,
        integrationId: Long,
    ) {
        val integration = mailIntegrationRepository.findByIdAndUserIdAndActiveTrue(integrationId, userId)
            ?: throw BusinessException(ErrorCode.MAIL_INTEGRATION_NOT_FOUND)
        integration.disconnect()
    }

    private fun generateState(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

data class MailOAuthConnectionResult(
    val connected: MailIntegrationConnectedResponse,
    val redirectAfter: String?,
)

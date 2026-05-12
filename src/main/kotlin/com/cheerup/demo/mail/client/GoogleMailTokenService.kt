package com.cheerup.demo.mail.client

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.oauth.MailOAuthProperties
import com.cheerup.demo.mail.oauth.MailTokenCipher
import com.cheerup.demo.mail.repository.MailIntegrationRepository
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Instant

@Service
class GoogleMailTokenService(
    private val mailIntegrationRepository: MailIntegrationRepository,
    private val mailTokenCipher: MailTokenCipher,
    private val mailOAuthProperties: MailOAuthProperties,
    private val mailClientProperties: MailClientProperties,
) {
    private val restClient: RestClient = RestClient.create()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resolveAccessToken(integration: MailIntegrationContext): String {
        val currentAccessToken = integration.accessToken
            ?: throw BusinessException(
                ErrorCode.MAIL_ACCESS_TOKEN_MISSING,
                detail = "integrationId=${integration.integrationId}",
            )

        if (!shouldRefresh(integration.expiresAt)) {
            return currentAccessToken
        }

        val integrationId = integration.integrationId
            ?: throw BusinessException(ErrorCode.MAIL_INTEGRATION_REAUTH_REQUIRED)
        val currentRefreshToken = integration.refreshToken
            ?: throw BusinessException(
                ErrorCode.MAIL_REFRESH_TOKEN_MISSING,
                detail = "integrationId=$integrationId",
            )

        val persisted = mailIntegrationRepository.findByIdAndUserIdAndActiveTrue(
            id = integrationId,
            userId = integration.userId,
        ) ?: throw BusinessException(
            ErrorCode.MAIL_INTEGRATION_NOT_FOUND,
            detail = "integrationId=$integrationId",
        )

        val refreshed = refreshAccessToken(currentRefreshToken)
        val expiresAt = refreshed.expiresIn?.let { Instant.now().plusSeconds(it) }

        persisted.accessToken = mailTokenCipher.encrypt(refreshed.accessToken)
        if (!refreshed.refreshToken.isNullOrBlank()) {
            persisted.refreshToken = mailTokenCipher.encrypt(refreshed.refreshToken)
        }
        if (expiresAt != null) {
            persisted.expiresAt = expiresAt
        }
        if (!refreshed.scope.isNullOrBlank()) {
            persisted.scope = refreshed.scope
        }

        return refreshed.accessToken
    }

    private fun shouldRefresh(expiresAt: Instant?): Boolean {
        if (expiresAt == null) {
            return true
        }

        val skew = mailClientProperties.gmail.tokenRefreshSkewSeconds.coerceAtLeast(0)
        return !expiresAt.isAfter(Instant.now().plusSeconds(skew))
    }

    private fun refreshAccessToken(refreshToken: String): GoogleRefreshTokenResponse {
        val google = mailOAuthProperties.providers.google
        if (google.clientId.isBlank() || google.clientSecret.isBlank()) {
            throw BusinessException(
                ErrorCode.MAIL_OAUTH_PROVIDER_NOT_CONFIGURED,
                detail = "provider=GOOGLE",
            )
        }

        val request = LinkedMultiValueMap<String, String>().apply {
            add("client_id", google.clientId)
            add("client_secret", google.clientSecret)
            add("refresh_token", refreshToken)
            add("grant_type", "refresh_token")
        }

        return try {
            restClient.post()
                .uri(google.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(request)
                .retrieve()
                .body(GoogleRefreshTokenResponse::class.java)
        } catch (exception: RestClientResponseException) {
            throw BusinessException(
                ErrorCode.MAIL_INTEGRATION_REAUTH_REQUIRED,
                detail = exception.toProviderErrorDetail("google.token.refresh"),
                cause = exception,
            )
        } catch (exception: RestClientException) {
            throw BusinessException(
                ErrorCode.MAIL_INTEGRATION_REAUTH_REQUIRED,
                detail = "google.token.refresh: ${exception.message}",
                cause = exception,
            )
        } ?: throw BusinessException(ErrorCode.MAIL_INTEGRATION_REAUTH_REQUIRED)
    }

    private fun RestClientResponseException.toProviderErrorDetail(operation: String): String {
        val body = responseBodyAsString.take(MAX_ERROR_BODY_LENGTH)
        return "$operation failed: status=${statusCode.value()}, body=$body"
    }

    companion object {
        private const val MAX_ERROR_BODY_LENGTH = 500
    }
}

private data class GoogleRefreshTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null,
    val scope: String? = null,
)

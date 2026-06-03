package com.cheerup.demo.mail.oauth

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.domain.MailProvider
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

@Component
class GoogleMailOAuthProvider(
    private val properties: MailOAuthProperties,
) : MailOAuthProvider {

    private val restClient: RestClient = RestClient.create()

    override fun supports(provider: MailProvider): Boolean = provider == MailProvider.GOOGLE

    override fun buildAuthorizationUrl(command: MailOAuthAuthorizeCommand): String {
        val google = properties.providers.google
        validateConfigured(google)

        return UriComponentsBuilder
            .fromUriString(google.authorizationUri)
            .queryParam("client_id", google.clientId)
            .queryParam("redirect_uri", google.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", google.scopes.joinToString(" "))
            .queryParam("state", command.state)
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .build()
            .encode()
            .toUriString()
    }

    override fun exchangeCode(command: MailOAuthCallbackCommand): MailOAuthToken {
        val google = properties.providers.google
        validateConfigured(google)

        val request = LinkedMultiValueMap<String, String>().apply {
            add("code", command.code)
            add("client_id", google.clientId)
            add("client_secret", google.clientSecret)
            add("redirect_uri", google.redirectUri)
            add("grant_type", "authorization_code")
        }

        val response = try {
            restClient.post()
                .uri(google.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(request)
                .retrieve()
                .body(GoogleTokenResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.MAIL_OAUTH_TOKEN_EXCHANGE_FAILED, cause = exception)
        } ?: throw BusinessException(ErrorCode.MAIL_OAUTH_TOKEN_EXCHANGE_FAILED)

        val expiresAt = response.expiresIn
            ?.let { Instant.now().plusSeconds(it) }

        return MailOAuthToken(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAt = expiresAt,
            scope = response.scope,
        )
    }

    override fun fetchAccount(token: MailOAuthToken): MailOAuthAccount {
        val google = properties.providers.google
        val response = try {
            restClient.get()
                .uri(google.userInfoUri)
                .headers { it.setBearerAuth(token.accessToken) }
                .retrieve()
                .body(GoogleUserInfoResponse::class.java)
        } catch (exception: RestClientException) {
            throw BusinessException(ErrorCode.MAIL_OAUTH_ACCOUNT_FETCH_FAILED, cause = exception)
        } ?: throw BusinessException(ErrorCode.MAIL_OAUTH_ACCOUNT_FETCH_FAILED)

        return MailOAuthAccount(
            providerAccountId = response.sub,
            email = response.email,
            displayName = response.name,
        )
    }

    private fun validateConfigured(google: GoogleMailOAuthProperties) {
        if (google.clientId.isBlank() || google.clientSecret.isBlank() || google.redirectUri.isBlank()) {
            throw BusinessException(
                ErrorCode.MAIL_OAUTH_PROVIDER_NOT_CONFIGURED,
                detail = "provider=${MailProvider.GOOGLE}",
            )
        }
    }
}

private data class GoogleTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null,
    val scope: String? = null,
)

private data class GoogleUserInfoResponse(
    val sub: String,
    val email: String,
    val name: String? = null,
)

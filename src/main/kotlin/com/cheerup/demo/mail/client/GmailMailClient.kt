package com.cheerup.demo.mail.client

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.mail.domain.MailProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(
    prefix = "app.mail.clients",
    name = ["google"],
    havingValue = "gmail",
)
class GmailMailClient(
    private val googleMailTokenService: GoogleMailTokenService,
    private val mailClientProperties: MailClientProperties,
    private val objectMapper: ObjectMapper,
) : MailClient {
    private val restClient: RestClient = RestClient.create()
    private val messageMapper = GmailMessageMapper()

    override fun supports(provider: MailProvider): Boolean =
        provider == MailProvider.GOOGLE

    override fun listMessages(integration: MailIntegrationContext, limit: Int): List<MailMessageCandidate> {
        val accessToken = googleMailTokenService.resolveAccessToken(integration)
        val maxResults = limit.coerceIn(1, mailClientProperties.gmail.maxResults.coerceAtLeast(1))

        val summaries = listMessageSummaries(
            accessToken = accessToken,
            maxResults = maxResults,
        )

        return summaries.map { summary ->
            val message = getMessage(
                accessToken = accessToken,
                messageId = summary.id,
                format = "metadata",
            )
            messageMapper.toCandidate(integration, message)
        }
    }

    override fun getMessageContent(
        integration: MailIntegrationContext,
        messageId: String,
    ): MailMessageContent {
        val accessToken = googleMailTokenService.resolveAccessToken(integration)
        val message = getMessage(accessToken, messageId, "full")
        return try {
            messageMapper.toContent(message)
        } catch (exception: RuntimeException) {
            throw BusinessException(
                ErrorCode.MAIL_PROVIDER_API_FAILED,
                detail = "gmail.messages.get returned an unreadable body",
                cause = exception,
            )
        }
    }

    private fun listMessageSummaries(
        accessToken: String,
        maxResults: Int,
    ): List<GmailMessageSummary> {
        val response = try {
            restClient.get()
                .uri { builder ->
                    builder
                        .scheme("https")
                        .host("gmail.googleapis.com")
                        .path("/gmail/v1/users/me/messages")
                        .queryParam("maxResults", maxResults)
                        .apply {
                            val query = mailClientProperties.gmail.messageQuery
                            if (query.isNotBlank()) {
                                queryParam("q", query)
                            }
                        }
                        .build()
                }
                .headers { it.setBearerAuth(accessToken) }
                .retrieve()
                .body(GmailMessageListResponse::class.java)
        } catch (exception: RestClientResponseException) {
            throw BusinessException(
                ErrorCode.MAIL_PROVIDER_API_FAILED,
                detail = exception.toProviderErrorDetail("gmail.messages.list"),
                cause = exception,
            )
        } catch (exception: RestClientException) {
            throw BusinessException(
                ErrorCode.MAIL_PROVIDER_API_FAILED,
                detail = "gmail.messages.list: ${exception.message}",
                cause = exception,
            )
        } ?: throw BusinessException(ErrorCode.MAIL_PROVIDER_API_FAILED)

        return response.messages.orEmpty()
    }

    private fun getMessage(
        accessToken: String,
        messageId: String,
        format: String,
    ): GmailMessageResponse {
        val responseBody = try {
            restClient.get()
                .uri { builder ->
                    builder
                        .scheme("https")
                        .host("gmail.googleapis.com")
                        .path("/gmail/v1/users/me/messages/{messageId}")
                        .queryParam("format", format)
                        .apply {
                            if (format == "metadata") {
                                queryParam("metadataHeaders", "Subject")
                                queryParam("metadataHeaders", "From")
                                queryParam("metadataHeaders", "Date")
                            }
                        }
                        .build(messageId)
                }
                .headers { it.setBearerAuth(accessToken) }
                .retrieve()
                .body(String::class.java)
        } catch (exception: RestClientResponseException) {
            throw BusinessException(
                ErrorCode.MAIL_PROVIDER_API_FAILED,
                detail = exception.toProviderErrorDetail("gmail.messages.get"),
                cause = exception,
            )
        } catch (exception: RestClientException) {
            throw BusinessException(
                ErrorCode.MAIL_PROVIDER_API_FAILED,
                detail = "gmail.messages.get: ${exception.message}",
                cause = exception,
            )
        } ?: throw BusinessException(ErrorCode.MAIL_PROVIDER_API_FAILED)

        return try {
            objectMapper.readValue(responseBody, GmailMessageResponse::class.java)
        } catch (exception: JacksonException) {
            throw BusinessException(
                ErrorCode.MAIL_PROVIDER_API_FAILED,
                detail = exception.toDeserializationErrorDetail("gmail.messages.get"),
                cause = exception,
            )
        }
    }

    private fun RestClientResponseException.toProviderErrorDetail(operation: String): String {
        val body = responseBodyAsString.take(MAX_ERROR_BODY_LENGTH)
        return "$operation failed: status=${statusCode.value()}, body=$body"
    }

    private fun JacksonException.toDeserializationErrorDetail(operation: String): String {
        val path = runCatching {
            javaClass.methods
                .firstOrNull { it.name == "getPathReference" && it.parameterCount == 0 }
                ?.invoke(this)
                ?.toString()
        }.getOrNull()

        return buildString {
            append("$operation returned invalid JSON: type=${javaClass.simpleName}")
            if (!path.isNullOrBlank()) {
                append(", path=")
                append(path.take(MAX_ERROR_PATH_LENGTH))
            }
        }
    }

    companion object {
        private const val MAX_ERROR_BODY_LENGTH = 500
        private const val MAX_ERROR_PATH_LENGTH = 300
    }
}

package com.cheerup.demo.mail.controller

import com.cheerup.demo.global.auth.AssignUserId
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.mail.api.MailIntegrationApi
import com.cheerup.demo.mail.domain.MailProvider
import com.cheerup.demo.mail.dto.MailIntegrationResponse
import com.cheerup.demo.mail.dto.MailOAuthAuthorizeResponse
import com.cheerup.demo.mail.oauth.MailOAuthProperties
import com.cheerup.demo.mail.service.MailOAuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder

@RestController
@RequestMapping("/api/mail/integrations")
class MailIntegrationController(
    private val mailOAuthService: MailOAuthService,
    private val properties: MailOAuthProperties,
) : MailIntegrationApi {

    @AssignUserId
    @GetMapping("/oauth/{provider}/authorize")
    override fun authorize(
        userId: Long,
        @PathVariable provider: String,
        @RequestParam(required = false) redirectAfter: String?,
    ): ApiResponse<MailOAuthAuthorizeResponse> =
        ApiResponse.success(
            MailOAuthAuthorizeResponse(
                authorizationUrl = mailOAuthService.buildAuthorizationUrl(
                    userId = userId,
                    provider = parseProvider(provider),
                    redirectAfter = redirectAfter,
                ),
            ),
        )

    @GetMapping("/oauth/{provider}/callback")
    override fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): RedirectView {
        val mailProvider = parseProvider(provider)

        if (error != null) {
            return RedirectView(failureRedirect(mailProvider, error))
        }
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            return RedirectView(failureRedirect(mailProvider, "invalid_callback"))
        }

        return runCatching {
            mailOAuthService.connectByAuthorizationCode(
                provider = mailProvider,
                code = code,
                stateValue = state,
            )
        }.fold(
            onSuccess = { result ->
                RedirectView(
                    successRedirect(
                        provider = mailProvider,
                        integrationId = result.connected.integrationId,
                        email = result.connected.email,
                        redirectAfter = result.redirectAfter,
                    ),
                )
            },
            onFailure = { exception ->
                val errorCode = (exception as? BusinessException)?.errorCode?.code ?: "MAIL_OAUTH_FAILED"
                RedirectView(failureRedirect(mailProvider, errorCode))
            },
        )
    }

    @AssignUserId
    @GetMapping
    override fun list(
        userId: Long,
    ): ApiResponse<List<MailIntegrationResponse>> =
        ApiResponse.success(mailOAuthService.listIntegrations(userId))

    @AssignUserId
    @DeleteMapping("/{integrationId}")
    override fun disconnect(
        userId: Long,
        @PathVariable integrationId: Long,
    ): ResponseEntity<Void> {
        mailOAuthService.disconnect(userId, integrationId)
        return ResponseEntity.noContent().build()
    }

    private fun successRedirect(
        provider: MailProvider,
        integrationId: Long,
        email: String,
        redirectAfter: String?,
    ): String =
        UriComponentsBuilder
            .fromUriString(properties.frontendSuccessRedirectUri)
            .queryParam("provider", provider.name)
            .queryParam("integrationId", integrationId)
            .queryParam("email", email)
            .apply {
                if (!redirectAfter.isNullOrBlank()) {
                    queryParam("redirectAfter", redirectAfter)
                }
            }
            .build()
            .encode()
            .toUriString()

    private fun failureRedirect(
        provider: MailProvider,
        error: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(properties.frontendFailureRedirectUri)
            .queryParam("provider", provider.name)
            .queryParam("error", error)
            .build()
            .encode()
            .toUriString()

    private fun parseProvider(provider: String): MailProvider =
        MailProvider.entries.firstOrNull { it.name.equals(provider, ignoreCase = true) }
            ?: throw BusinessException(
                ErrorCode.MAIL_OAUTH_PROVIDER_NOT_SUPPORTED,
                detail = "provider=$provider",
            )
}

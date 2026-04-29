package com.cheerup.demo.global.oauth

import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId

        val userInfo = when (registrationId) {
            "google" -> GoogleOAuth2UserInfo(oauth2User.attributes)
            else -> throw OAuth2AuthenticationException(
                OAuth2Error(ErrorCode.OAUTH2_PROVIDER_NOT_SUPPORTED.code),
                ErrorCode.OAUTH2_PROVIDER_NOT_SUPPORTED.message,
            )
        }

        return OAuth2UserPrincipal(
            userInfo = userInfo,
            delegateAttributes = oauth2User.attributes,
            delegateAuthorities = oauth2User.authorities,
        )
    }
}

package com.cheerup.demo.global.oauth

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val oAuth2UserInfoFactory: OAuth2UserInfoFactory,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val userInfo = oAuth2UserInfoFactory.create(registrationId, oauth2User.attributes)

        return DefaultOAuth2UserPrincipal(
            userInfo = userInfo,
            delegateAttributes = oauth2User.attributes,
            delegateAuthorities = oauth2User.authorities,
        )
    }
}

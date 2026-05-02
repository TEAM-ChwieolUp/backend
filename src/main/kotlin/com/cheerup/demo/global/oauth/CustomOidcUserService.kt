package com.cheerup.demo.global.oauth

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class CustomOidcUserService(
    private val oAuth2UserInfoFactory: OAuth2UserInfoFactory,
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val userInfo = oAuth2UserInfoFactory.create(registrationId, oidcUser.claims)

        return OidcOAuth2UserPrincipal(
            userInfo = userInfo,
            delegate = oidcUser,
        )
    }
}

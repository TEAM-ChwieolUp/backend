package com.cheerup.demo.global.oauth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class OidcOAuth2UserPrincipal(
    override val userInfo: OAuth2UserInfo,
    private val delegate: OidcUser,
) : OidcUser, OAuth2AuthenticationPrincipal {
    override fun getName(): String = delegate.name

    override fun getClaims(): Map<String, Any> = delegate.claims

    override fun getAttributes(): Map<String, Any> = delegate.attributes

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = delegate.authorities

    override fun getUserInfo(): OidcUserInfo? = delegate.userInfo

    override fun getIdToken(): OidcIdToken = delegate.idToken
}

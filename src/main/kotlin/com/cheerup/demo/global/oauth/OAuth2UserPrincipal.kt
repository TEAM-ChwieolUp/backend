package com.cheerup.demo.global.oauth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class OAuth2UserPrincipal(
    val userInfo: OAuth2UserInfo,
    private val delegateAttributes: Map<String, Any>,
    private val delegateAuthorities: Collection<GrantedAuthority>,
) : OAuth2User {
    override fun getName(): String = userInfo.providerUserId

    override fun getAttributes(): Map<String, Any> = delegateAttributes

    override fun getAuthorities(): Collection<GrantedAuthority> = delegateAuthorities
}

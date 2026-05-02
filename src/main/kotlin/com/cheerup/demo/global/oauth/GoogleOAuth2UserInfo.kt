package com.cheerup.demo.global.oauth

import com.cheerup.demo.user.domain.OAuth2Provider

class GoogleOAuth2UserInfo(
    private val attributes: Map<String, Any>,
) : OAuth2UserInfo {
    override val provider: OAuth2Provider = OAuth2Provider.GOOGLE
    override val providerUserId: String = requireString("sub")
    override val email: String? = attributes["email"] as? String
    override val name: String? = attributes["name"] as? String
    override val profileImageUrl: String? = attributes["picture"] as? String

    private fun requireString(key: String): String =
        attributes[key] as? String
            ?: throw IllegalArgumentException("Missing OAuth2 attribute: $key")
}

package com.cheerup.demo.global.oauth

import com.cheerup.demo.user.domain.OAuth2Provider

class KakaoOAuth2UserInfo(
    private val attributes: Map<String, Any>,
) : OAuth2UserInfo {
    override val provider: OAuth2Provider = OAuth2Provider.KAKAO

    override val providerUserId: String = requireFirstString("sub", "id")

    override val email: String? =
        attributes["email"] as? String
            ?: kakaoAccount["email"] as? String

    override val name: String? =
        attributes["nickname"] as? String
            ?: attributes["name"] as? String
            ?: profile["nickname"] as? String
            ?: properties["nickname"] as? String

    override val profileImageUrl: String? =
        attributes["picture"] as? String
            ?: attributes["profile_image"] as? String
            ?: profile["profile_image_url"] as? String
            ?: properties["profile_image"] as? String

    @Suppress("UNCHECKED_CAST")
    private val kakaoAccount: Map<String, Any>
        get() = attributes["kakao_account"] as? Map<String, Any> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private val profile: Map<String, Any>
        get() = kakaoAccount["profile"] as? Map<String, Any> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private val properties: Map<String, Any>
        get() = attributes["properties"] as? Map<String, Any> ?: emptyMap()

    private fun requireFirstString(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> attributes[key]?.toString() }
            ?: throw IllegalArgumentException("Missing OAuth2 attribute: ${keys.joinToString(" or ")}")
}

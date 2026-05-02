package com.cheerup.demo.global.oauth

import com.cheerup.demo.user.domain.OAuth2Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OAuth2UserInfoFactoryTest {

    private val factory = OAuth2UserInfoFactory()

    @Test
    fun `google user info maps expected fields`() {
        val userInfo = factory.create(
            registrationId = "google",
            attributes = mapOf(
                "sub" to "google-sub-1",
                "email" to "google@example.com",
                "name" to "Google User",
                "picture" to "https://example.com/google.png",
            ),
        )

        assertEquals(OAuth2Provider.GOOGLE, userInfo.provider)
        assertEquals("google-sub-1", userInfo.providerUserId)
        assertEquals("google@example.com", userInfo.email)
        assertEquals("Google User", userInfo.name)
        assertEquals("https://example.com/google.png", userInfo.profileImageUrl)
    }

    @Test
    fun `kakao user info maps expected nested fields`() {
        val userInfo = factory.create(
            registrationId = "kakao",
            attributes = mapOf(
                "id" to 123456789L,
                "kakao_account" to mapOf(
                    "email" to "kakao@example.com",
                    "profile" to mapOf(
                        "nickname" to "Kakao User",
                        "profile_image_url" to "https://example.com/kakao.png",
                    ),
                ),
            ),
        )

        assertEquals(OAuth2Provider.KAKAO, userInfo.provider)
        assertEquals("123456789", userInfo.providerUserId)
        assertEquals("kakao@example.com", userInfo.email)
        assertEquals("Kakao User", userInfo.name)
        assertEquals("https://example.com/kakao.png", userInfo.profileImageUrl)
    }

    @Test
    fun `kakao oidc user info maps expected standard claims`() {
        val userInfo = factory.create(
            registrationId = "kakao",
            attributes = mapOf(
                "sub" to "kakao-sub-1",
                "email" to "kakao@example.com",
                "nickname" to "Kakao Oidc User",
                "picture" to "https://example.com/kakao-oidc.png",
            ),
        )

        assertEquals(OAuth2Provider.KAKAO, userInfo.provider)
        assertEquals("kakao-sub-1", userInfo.providerUserId)
        assertEquals("kakao@example.com", userInfo.email)
        assertEquals("Kakao Oidc User", userInfo.name)
        assertEquals("https://example.com/kakao-oidc.png", userInfo.profileImageUrl)
    }

    @Test
    fun `kakao user info allows missing email`() {
        val userInfo = factory.create(
            registrationId = "kakao",
            attributes = mapOf(
                "id" to 123456789L,
                "properties" to mapOf(
                    "nickname" to "Kakao User",
                    "profile_image" to "https://example.com/kakao.png",
                ),
            ),
        )

        assertNull(userInfo.email)
        assertEquals("Kakao User", userInfo.name)
        assertEquals("https://example.com/kakao.png", userInfo.profileImageUrl)
    }
}

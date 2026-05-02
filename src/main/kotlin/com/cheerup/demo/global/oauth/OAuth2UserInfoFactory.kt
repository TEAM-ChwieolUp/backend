package com.cheerup.demo.global.oauth

import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.stereotype.Component

@Component
class OAuth2UserInfoFactory {

    fun create(
        registrationId: String,
        attributes: Map<String, Any>,
    ): OAuth2UserInfo =
        when (registrationId) {
            "google" -> GoogleOAuth2UserInfo(attributes)
            "kakao" -> KakaoOAuth2UserInfo(attributes)
            else -> throw OAuth2AuthenticationException(
                OAuth2Error(ErrorCode.OAUTH2_PROVIDER_NOT_SUPPORTED.code),
                ErrorCode.OAUTH2_PROVIDER_NOT_SUPPORTED.message,
            )
        }
}

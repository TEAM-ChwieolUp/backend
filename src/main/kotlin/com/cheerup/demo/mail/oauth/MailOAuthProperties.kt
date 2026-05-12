package com.cheerup.demo.mail.oauth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.mail.oauth")
class MailOAuthProperties {
    var frontendSuccessRedirectUri: String = ""
    var frontendFailureRedirectUri: String = ""
    var stateTtlMinutes: Long = 10
    var tokenEncryptionKey: String = ""
    var providers: MailOAuthProviderProperties = MailOAuthProviderProperties()
}

class MailOAuthProviderProperties {
    var google: GoogleMailOAuthProperties = GoogleMailOAuthProperties()
}

class GoogleMailOAuthProperties {
    var clientId: String = ""
    var clientSecret: String = ""
    var redirectUri: String = ""
    var authorizationUri: String = "https://accounts.google.com/o/oauth2/v2/auth"
    var tokenUri: String = "https://oauth2.googleapis.com/token"
    var userInfoUri: String = "https://openidconnect.googleapis.com/v1/userinfo"
    var scopes: List<String> = listOf(
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/gmail.readonly",
    )
}

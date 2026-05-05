package com.cheerup.demo.global.oauth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.auth.oauth2")
class OAuth2RedirectProperties {
    var successRedirectUri: String = "http://localhost:3000/auth/callback"
}

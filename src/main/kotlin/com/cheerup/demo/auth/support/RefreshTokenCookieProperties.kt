package com.cheerup.demo.auth.support

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.auth.refresh-token-cookie")
class RefreshTokenCookieProperties {
    var secure: Boolean = true
    var sameSite: String = "Lax"
}

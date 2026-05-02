package com.cheerup.demo.global.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.security.jwt")
class JwtProperties {
    var secret: String = "change-me-change-me-change-me-change-me-change-me"
    var issuer: String = "cheerup"
    var accessTokenExpiration: Duration = Duration.ofMinutes(15)
    var refreshTokenExpiration: Duration = Duration.ofDays(14)
}

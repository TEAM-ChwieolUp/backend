package com.cheerup.demo.mail.client

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.mail")
class MailClientProperties {
    var clients: MailClientsProperties = MailClientsProperties()
    var gmail: GmailClientProperties = GmailClientProperties()
}

class MailClientsProperties {
    var google: String = "stub"
    var naver: String = "disabled"
    var outlook: String = "disabled"
}

class GmailClientProperties {
    var messageQuery: String = "newer_than:30d"
    var maxResults: Int = 50
    var tokenRefreshSkewSeconds: Long = 60
}

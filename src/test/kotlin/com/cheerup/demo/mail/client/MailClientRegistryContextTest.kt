package com.cheerup.demo.mail.client

import com.cheerup.demo.mail.domain.MailProvider
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    properties = [
        "app.mail.clients.google=gmail",
    ],
)
@ActiveProfiles("test")
class MailClientRegistryContextTest {

    @Autowired
    private lateinit var mailClientRegistry: MailClientRegistry

    @Test
    fun `registers Gmail client when google client mode is gmail`() {
        val mailClient = mailClientRegistry.get(MailProvider.GOOGLE)

        assertInstanceOf(GmailMailClient::class.java, mailClient)
    }
}

package com.cheerup.demo.mail.repository

import com.cheerup.demo.mail.domain.MailIntegration
import com.cheerup.demo.mail.domain.MailProvider
import org.springframework.data.jpa.repository.JpaRepository

interface MailIntegrationRepository : JpaRepository<MailIntegration, Long> {
    fun findAllByUserIdAndActiveTrueOrderByIdAsc(userId: Long): List<MailIntegration>

    fun findByIdAndUserIdAndActiveTrue(id: Long, userId: Long): MailIntegration?

    fun findByUserIdAndProviderAndProviderAccountId(
        userId: Long,
        provider: MailProvider,
        providerAccountId: String,
    ): MailIntegration?
}

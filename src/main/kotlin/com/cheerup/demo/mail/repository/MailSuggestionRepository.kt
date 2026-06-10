package com.cheerup.demo.mail.repository

import com.cheerup.demo.mail.domain.MailSuggestion
import com.cheerup.demo.mail.domain.MailSuggestionStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MailSuggestionRepository : JpaRepository<MailSuggestion, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): MailSuggestion?

    fun findAllByUserIdAndStatusOrderByIdDesc(userId: Long, status: MailSuggestionStatus): List<MailSuggestion>

    fun findFirstByUserIdAndIntegrationIdAndMessageIdAndApplicationIdAndStatusOrderByIdDesc(
        userId: Long,
        integrationId: Long,
        messageId: String,
        applicationId: Long,
        status: MailSuggestionStatus,
    ): MailSuggestion?
}

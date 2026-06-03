package com.cheerup.demo.mail.repository

import com.cheerup.demo.mail.domain.MailOAuthState
import org.springframework.data.jpa.repository.JpaRepository

interface MailOAuthStateRepository : JpaRepository<MailOAuthState, Long> {
    fun findByState(state: String): MailOAuthState?
}

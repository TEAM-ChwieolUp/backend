package com.cheerup.demo.mail.domain

import com.cheerup.demo.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "mail_oauth_states",
    indexes = [
        Index(name = "idx_mail_oauth_states_state", columnList = "state", unique = true),
        Index(name = "idx_mail_oauth_states_user_id", columnList = "user_id"),
    ],
)
class MailOAuthState(
    @Column(nullable = false, length = 100, unique = true)
    val state: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val provider: MailProvider,

    @Column(name = "redirect_after", length = 1000)
    val redirectAfter: String? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)

    fun consume(now: Instant) {
        consumedAt = now
    }
}

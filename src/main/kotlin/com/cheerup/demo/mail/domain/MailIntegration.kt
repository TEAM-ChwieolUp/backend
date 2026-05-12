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
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "mail_integrations",
    indexes = [
        Index(name = "idx_mail_integrations_user_id_active", columnList = "user_id,active"),
        Index(name = "idx_mail_integrations_user_id_provider", columnList = "user_id,provider"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_mail_integrations_user_provider_account",
            columnNames = ["user_id", "provider", "provider_account_id"],
        ),
    ],
)
class MailIntegration(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val provider: MailProvider,

    @Column(name = "provider_account_id", nullable = false, length = 255)
    val providerAccountId: String,

    @Column(nullable = false, length = 255)
    var email: String,

    @Column(name = "display_name", length = 100)
    var displayName: String? = null,

    @Column(name = "access_token", length = 2000)
    var accessToken: String? = null,

    @Column(name = "refresh_token", length = 2000)
    var refreshToken: String? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(length = 1000)
    var scope: String? = null,

    @Column(name = "connected_at", nullable = false)
    val connectedAt: Instant = Instant.now(),

    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,

    @Column(nullable = false)
    var active: Boolean = true,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun reconnect(
        email: String,
        displayName: String?,
        accessToken: String?,
        refreshToken: String?,
        expiresAt: Instant?,
        scope: String?,
    ) {
        this.email = email
        this.displayName = displayName
        this.accessToken = accessToken
        if (refreshToken != null) {
            this.refreshToken = refreshToken
        }
        this.expiresAt = expiresAt
        this.scope = scope
        this.active = true
    }

    fun disconnect() {
        active = false
    }
}

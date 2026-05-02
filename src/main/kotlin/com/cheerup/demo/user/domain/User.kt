package com.cheerup.demo.user.domain

import com.cheerup.demo.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_users_provider_provider_user_id",
            columnNames = ["oauth2_provider", "provider_user_id"],
        ),
    ],
)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "oauth2_provider", nullable = false, length = 50)
    var oauth2Provider: OAuth2Provider,
    @Column(name = "provider_user_id", nullable = false, length = 100)
    var providerUserId: String,
    @Column(name = "email", nullable = false, length = 255)
    var email: String,
    @Column(name = "name", length = 100)
    var name: String? = null,
    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null,
) : BaseEntity()

package com.cheerup.demo.application.domain

import com.cheerup.demo.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "tags",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tags_user_id_name", columnNames = ["user_id", "name"]),
    ],
)
class Tag(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 30)
    var name: String,

    @Column(nullable = false, length = 7)
    var color: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

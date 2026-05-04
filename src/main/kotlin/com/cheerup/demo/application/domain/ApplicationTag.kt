package com.cheerup.demo.application.domain

import com.cheerup.demo.global.base.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "application_tags",
    indexes = [
        Index(name = "idx_application_tags_tag_id", columnList = "tag_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_application_tags_application_id_tag_id",
            columnNames = ["application_id", "tag_id"],
        ),
    ],
)
class ApplicationTag(
    @Column(name = "application_id", nullable = false)
    val applicationId: Long,

    @Column(name = "tag_id", nullable = false)
    val tagId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

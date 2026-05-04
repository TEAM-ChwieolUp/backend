package com.cheerup.demo.application.domain

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

@Entity
@Table(
    name = "stages",
    indexes = [
        Index(name = "idx_stages_user_id_display_order", columnList = "user_id,display_order"),
    ],
)
class Stage(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 30)
    var name: String,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,

    @Column(nullable = false, length = 7)
    var color: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val category: StageCategory,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

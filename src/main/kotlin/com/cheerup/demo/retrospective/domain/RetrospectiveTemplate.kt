package com.cheerup.demo.retrospective.domain

import com.cheerup.demo.global.base.BaseEntity
import com.cheerup.demo.global.persistence.StringListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "retrospective_templates",
    indexes = [
        Index(name = "idx_retrospective_templates_user_id", columnList = "user_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_retrospective_templates_user_id_name",
            columnNames = ["user_id", "name"],
        ),
    ],
)
class RetrospectiveTemplate(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 50)
    var name: String,

    @Convert(converter = StringListConverter::class)
    @Column(columnDefinition = "JSON", nullable = false)
    var questions: MutableList<String> = mutableListOf(),
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun update(name: String, questions: List<String>) {
        this.name = name
        this.questions = questions.toMutableList()
    }
}

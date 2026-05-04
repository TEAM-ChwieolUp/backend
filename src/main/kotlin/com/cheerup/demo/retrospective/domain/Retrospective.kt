package com.cheerup.demo.retrospective.domain

import com.cheerup.demo.global.base.BaseEntity
import com.cheerup.demo.global.persistence.RetrospectiveItemListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(
    name = "retrospectives",
    indexes = [
        Index(name = "idx_retrospectives_application_id", columnList = "application_id"),
        Index(name = "idx_retrospectives_user_id_stage_id", columnList = "user_id,stage_id"),
    ],
)
class Retrospective(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "application_id", nullable = false)
    val applicationId: Long,

    @Column(name = "stage_id")
    val stageId: Long?,

    @Convert(converter = RetrospectiveItemListConverter::class)
    @Column(columnDefinition = "JSON", nullable = false)
    var items: MutableList<RetrospectiveItem> = mutableListOf(),
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    fun addItem(item: RetrospectiveItem) {
        require(item.question.isNotBlank()) { "question must not be blank" }
        items = (items + item).toMutableList()
    }

    fun updateItem(index: Int, item: RetrospectiveItem) {
        require(index in items.indices) { "invalid item index: $index" }
        require(item.question.isNotBlank()) { "question must not be blank" }
        items = items.toMutableList().also { it[index] = item }
    }

    fun removeItem(index: Int) {
        require(index in items.indices) { "invalid item index: $index" }
        items = items.toMutableList().also { it.removeAt(index) }
    }

    fun appendQuestions(questions: List<String>) {
        val nextItems = questions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { RetrospectiveItem(question = it, answer = null) }

        if (nextItems.isNotEmpty()) {
            items = (items + nextItems).toMutableList()
        }
    }
}

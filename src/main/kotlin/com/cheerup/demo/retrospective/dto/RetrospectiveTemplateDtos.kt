package com.cheerup.demo.retrospective.dto

import com.cheerup.demo.retrospective.domain.RetrospectiveTemplate
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateRetrospectiveTemplateRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val name: String,

    @field:Size(max = 50)
    val questions: List<String> = emptyList(),
)

data class UpdateRetrospectiveTemplateRequest(
    @field:Size(min = 1, max = 50)
    val name: String? = null,

    @field:Size(max = 50)
    val questions: List<String>? = null,
) {
    fun isEmpty(): Boolean = name == null && questions == null
}

data class ApplyRetrospectiveTemplateRequest(
    @field:Positive
    val templateId: Long,
)

data class RetrospectiveTemplateResponse(
    val id: Long,
    val name: String,
    val questions: List<String>,
)

fun RetrospectiveTemplate.toResponse(): RetrospectiveTemplateResponse =
    RetrospectiveTemplateResponse(
        id = requireNotNull(id) { "RetrospectiveTemplate must be persisted" },
        name = name,
        questions = questions.toList(),
    )

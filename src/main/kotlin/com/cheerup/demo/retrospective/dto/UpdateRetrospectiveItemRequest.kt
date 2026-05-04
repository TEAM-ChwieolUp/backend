package com.cheerup.demo.retrospective.dto

import jakarta.validation.constraints.Size

data class UpdateRetrospectiveItemRequest(
    @field:Size(max = 1000)
    val question: String? = null,

    @field:Size(max = 5000)
    val answer: String? = null,
) {
    fun isEmpty(): Boolean = question == null && answer == null
}

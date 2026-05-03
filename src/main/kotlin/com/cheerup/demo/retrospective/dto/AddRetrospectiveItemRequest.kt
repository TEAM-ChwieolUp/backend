package com.cheerup.demo.retrospective.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddRetrospectiveItemRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 1000)
    val question: String,

    @field:Size(max = 5000)
    val answer: String? = null,
)

package com.cheerup.demo.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateTagRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    @field:NotBlank
    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a 6-digit hex code (e.g. #4F46E5)")
    val color: String,
)

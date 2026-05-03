package com.cheerup.demo.retrospective.ai

import com.cheerup.demo.application.domain.StageCategory

data class RetrospectiveQuestionContext(
    val companyName: String,
    val position: String,
    val memo: String?,
    val stageName: String?,
    val stageCategory: StageCategory?,
)

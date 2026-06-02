package com.cheerup.demo.retrospective.ai

data class RetrospectiveQuestionContext(
    val userId: Long,
    val jobPostingTitle: String,
    val companyName: String,
    val jobRole: String,
    val processStage: String,
    val questionCount: Int,
)

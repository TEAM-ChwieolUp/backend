package com.cheerup.demo.retrospective.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cheerup.ai.retrospective")
class RetrospectiveAiProperties {
    var baseUrl: String? = null
    var questionPath: String = "/ai/retrospective/questions"
    var connectTimeout: Duration = Duration.ofSeconds(1)
    var readTimeout: Duration = Duration.ofSeconds(8)
    var defaultQuestionCount: Int = 4
    var maxQuestionCount: Int = 15
    var dailyLimit: Int = 50

    fun requiredBaseUrl(): String =
        baseUrl?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("cheerup.ai.retrospective.base-url must be configured")
}

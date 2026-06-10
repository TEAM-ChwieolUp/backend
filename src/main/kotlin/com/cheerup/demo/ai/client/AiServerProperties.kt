package com.cheerup.demo.ai.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cheerup.ai.server")
class AiServerProperties {
    var baseUrl: String? = null
    var connectTimeout: Duration = Duration.ofSeconds(1)
    var readTimeout: Duration = Duration.ofSeconds(8)
    var mailStageClassifyPath: String = "/ai/mail/stage-classify"
    var kanbanMoveRecommendPath: String = "/ai/kanban/move-recommend"
    var retrospectiveQuestionsPath: String = "/ai/retrospective/questions"

    fun requiredBaseUrl(): String =
        baseUrl?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("cheerup.ai.server.base-url must be configured")
}

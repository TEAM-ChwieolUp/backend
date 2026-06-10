package com.cheerup.demo.retrospective.ai

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cheerup.ai.retrospective")
class RetrospectiveAiProperties {
    var defaultQuestionCount: Int = 4
    var maxQuestionCount: Int = 10
    var dailyLimit: Int = 50
}

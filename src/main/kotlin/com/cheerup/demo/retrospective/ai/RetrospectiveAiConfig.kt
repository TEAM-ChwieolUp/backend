package com.cheerup.demo.retrospective.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RetrospectiveAiProperties::class)
class RetrospectiveAiConfig

package com.cheerup.demo.retrospective.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(RetrospectiveAiProperties::class)
class RetrospectiveAiConfig {

    @Bean
    fun retrospectiveAiRestClient(
        properties: RetrospectiveAiProperties,
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }

        return RestClient.builder()
            .baseUrl(properties.requiredBaseUrl())
            .requestFactory(requestFactory)
            .build()
    }
}

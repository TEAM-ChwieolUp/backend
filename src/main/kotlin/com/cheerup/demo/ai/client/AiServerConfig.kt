package com.cheerup.demo.ai.client

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(AiServerProperties::class)
class AiServerConfig(
    private val environment: Environment,
) {

    @Bean
    fun aiServerRestClient(properties: AiServerProperties): RestClient {
        val baseUrl = properties.requiredBaseUrl()
        if (baseUrl.startsWith("http://") && isProductionProfile()) {
            throw IllegalStateException("Production AI server URL must use HTTPS")
        }

        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    @Bean(name = ["aiTaskExecutor"], destroyMethod = "shutdown")
    fun aiTaskExecutor(): ExecutorService =
        Executors.newVirtualThreadPerTaskExecutor()

    private fun isProductionProfile(): Boolean =
        environment.activeProfiles.contains("prod")
}

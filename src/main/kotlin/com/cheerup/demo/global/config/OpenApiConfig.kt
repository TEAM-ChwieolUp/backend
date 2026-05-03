package com.cheerup.demo.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OpenApiProperties::class)
class OpenApiConfig(
    private val openApiProperties: OpenApiProperties,
) {

    @Bean
    fun openAPI(): OpenAPI {
        val bearerSchemeName = "bearerAuth"

        return OpenAPI()
            .info(
                Info()
                    .title(openApiProperties.title)
                    .description(openApiProperties.description)
                    .version(openApiProperties.version),
            )
            .servers(
                openApiProperties.servers.map { server ->
                    Server()
                        .url(server.url)
                        .description(server.description)
                },
            )
            .components(
                Components()
                    .addSchemas(
                        "Meta",
                        ObjectSchema().properties(
                            mapOf(
                                "timestamp" to StringSchema().format("date-time"),
                                "requestId" to StringSchema().nullable(true),
                            ),
                        ),
                    )
                    .addSchemas(
                        "ErrorResponse",
                        ObjectSchema().properties(
                            mapOf(
                                "code" to StringSchema(),
                                "message" to StringSchema(),
                                "detail" to StringSchema().nullable(true),
                                "timestamp" to StringSchema().format("date-time"),
                                "path" to StringSchema().nullable(true),
                            ),
                        ),
                    )
                    .addSecuritySchemes(
                        bearerSchemeName,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT"),
                    ),
            )
    }
}

@ConfigurationProperties(prefix = "app.openapi")
data class OpenApiProperties(
    val title: String = "CheerUp API",
    val description: String = "ChwieolUp backend API documentation",
    val version: String = "v1",
    val servers: List<OpenApiServerProperties> = emptyList(),
)

data class OpenApiServerProperties(
    val url: String,
    val description: String,
)

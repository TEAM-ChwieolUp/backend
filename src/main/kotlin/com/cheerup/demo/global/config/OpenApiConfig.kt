package com.cheerup.demo.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("CheerUp API")
                    .description("취업 준비생을 위한 채용 칸반 보드 API")
                    .version("v1"),
            )
}

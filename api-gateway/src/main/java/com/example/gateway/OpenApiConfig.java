package com.example.gateway;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Video Service API")
                        .version("1.0.0")
                        .description("""
                                Микросервисная платформа для загрузки, обработки и стриминга видео.
                                
                                **Возможности:**
                                - Аутентификация через JWT
                                - Multipart загрузка видео напрямую в MinIO
                                - Адаптивный HLS-стриминг (1080p/720p/480p/360p/240p)
                                - Асинхронная обработка через Kafka + FFmpeg
                                - SSE-уведомления о статусе видео
                                """)
                        .contact(new Contact()
                                .name("Dmitrii")
                                .email("dmitrii@example.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Gateway")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT-токен из /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
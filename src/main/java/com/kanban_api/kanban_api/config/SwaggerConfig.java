package com.kanban_api.kanban_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() { //coloca para todos os metodos um Authorization
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Ambiente Local"),
                        new Server().url("https://kanban-report-api.com").description("Produção")
                ))
                .info(new Info().title("KanbanReports-API"))
                .addSecurityItem(new SecurityRequirement().addList("JavaInUseSecurityScheme"))
                .components(new Components().addSecuritySchemes("JavaInUseSecurityScheme", new SecurityScheme()
                        .name("JavaInUseSecurityScheme").type(SecurityScheme.Type.HTTP).scheme("Bearer").bearerFormat("JWT")));
    }

}

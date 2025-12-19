package com.maintenance_match.matching.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Value("${api-gateway.url}")
        private String gatewayUrl;

        @Bean
        public OpenAPI customOpenAPI() {
                final String securitySchemeName = "bearerAuth";
                return new OpenAPI()
                        .addServersItem(new Server().url(gatewayUrl).description("API Gateway URL"))
                        .components(new io.swagger.v3.oas.models.Components()
                                .addSecuritySchemes(securitySchemeName, new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                        .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(securitySchemeName))
                        .info(new io.swagger.v3.oas.models.info.Info().title("MaintenanceMatch - Matching API").version("v1.0"));
        }
}

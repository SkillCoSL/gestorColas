package com.queuetable.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI queueTableOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QueueTable API")
                        .version("0.1.0")
                        .description("API para gestion de colas, reservas y mesas de restaurantes en tiempo real")
                        .contact(new Contact()
                                .name("QueueTable")
                                .url("https://github.com/DanielRuiz-14/gestorColas")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Desarrollo local")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT — usar sin prefijo 'Bearer'")));
    }
}

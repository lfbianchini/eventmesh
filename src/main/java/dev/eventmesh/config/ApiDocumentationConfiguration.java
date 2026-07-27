package dev.eventmesh.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentationConfiguration {

  @Bean
  OpenAPI eventMeshOpenApi() {
    var apiKeyName = "eventMeshApiKey";
    return new OpenAPI()
        .info(
            new Info()
                .title("EventMesh API")
                .version("v1")
                .description(
                    "Multi-tenant durable event ingestion, webhook subscriptions, "
                        + "delivery inspection, and dead-letter replay.")
                .license(new License().name("MIT")))
        .components(
            new Components()
                .addSecuritySchemes(
                    apiKeyName,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-EventMesh-Key")))
        .addSecurityItem(new SecurityRequirement().addList(apiKeyName));
  }
}

package com.demo.file.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Swagger UI at {@code /swagger-ui.html} with Bearer token authentication.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "File Service API", version = "v1",
        description = "Upload files and resolve presigned download URLs"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}

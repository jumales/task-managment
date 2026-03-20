package com.demo.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Configures OpenAPI metadata for the User Service API documentation. */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description("Manages users, roles, and rights. Roles are collections of rights; users can be assigned one or more roles.")
                        .version("1.0.0")
                        .contact(new Contact().name("Demo Team").email("demo@example.com")));
    }
}

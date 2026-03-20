package com.demo.task.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Configures OpenAPI metadata for the Task Service API documentation. */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Service API")
                        .description("Manages tasks. Each task can be assigned to a user looked up from the User Service.")
                        .version("1.0.0")
                        .contact(new Contact().name("Demo Team").email("demo@example.com")));
    }
}

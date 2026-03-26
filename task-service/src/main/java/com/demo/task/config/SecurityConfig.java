package com.demo.task.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import com.demo.common.web.LoggingAuthenticationEntryPoint;

/**
 * Servlet security configuration for task-service.
 *
 * <p>Configures the service as a stateless OAuth2 resource server that validates
 * JWTs independently of the gateway (defense in depth). Roles and rights extracted
 * by {@link JwtAuthConverter} are available for {@code @PreAuthorize} expressions.
 *
 * <p>Example usage:
 * <pre>
 *   {@code @PreAuthorize("hasRole('ADMIN')")}
 *   {@code @PreAuthorize("hasAuthority('TASK_DELETE')")}
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;
    private final LoggingAuthenticationEntryPoint loggingEntryPoint;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter,
                          LoggingAuthenticationEntryPoint loggingEntryPoint) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.loggingEntryPoint = loggingEntryPoint;
    }

    /**
     * Configures stateless JWT validation; all endpoints require authentication.
     * Session creation is disabled — each request is authenticated via the Bearer token.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(loggingEntryPoint))
                .build();
    }
}

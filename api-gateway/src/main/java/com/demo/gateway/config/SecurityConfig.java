package com.demo.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Reactive security configuration for the API Gateway.
 *
 * <p>Validates the JWT on every incoming request before routing it downstream.
 * Requests with missing, expired, or invalid tokens are rejected with 401 here,
 * so downstream services never receive unauthenticated traffic through the gateway.
 *
 * <p>The Authorization header is forwarded unchanged to downstream services,
 * which independently validate the same JWT (defense in depth).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * Configures stateless JWT resource-server security for the reactive gateway.
     * All routes require a valid Bearer token; CSRF is disabled (stateless API).
     */
    /**
     * Configures stateless JWT resource-server security for the reactive gateway.
     * All routes require a valid Bearer token; CSRF is disabled (stateless API).
     * CORS is handled here so preflight OPTIONS requests are not blocked by security.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                )
                .build();
    }

    /** Allows requests from the web client dev server on both common ports. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

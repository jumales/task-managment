package com.demo.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits all HTTP-level requests to the WebSocket handshake paths ({@code /ws/tasks/**}).
 *
 * <p>SockJS negotiation (e.g. {@code GET /ws/tasks/info}) and the WebSocket upgrade request
 * do not carry a Bearer token at the HTTP layer. Authentication is instead enforced
 * at the STOMP protocol level by {@link WebSocketSecurityConfig}, which validates the
 * JWT on every STOMP CONNECT frame. This filter chain runs before the shared
 * {@code SecurityConfig} so those paths are not rejected with 401.
 */
@Configuration
public class WebSocketHttpSecurityConfig {

    /**
     * Higher-priority filter chain that opens {@code /ws/tasks/**} at the HTTP layer.
     * Must run before the common SecurityFilterChain (default order 100).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain webSocketSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/ws/tasks/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}

package com.demo.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits HTTP-level access to the WebSocket upgrade endpoint (SockJS fallback transports).
 * JWT validation is handled at the STOMP level by {@link WebSocketSecurityConfig}.
 * Active only when Spring WebSocket is on the classpath.
 *
 * <p>Configure the path matcher via {@code websocket.security.path-matcher} in
 * {@code application.yml} (default: {@code /ws/**}).
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer")
public class WebSocketHttpSecurityConfig {

    @Value("${websocket.security.path-matcher:/ws/**}")
    private String pathMatcher;

    /**
     * Higher-priority filter chain that opens the WebSocket handshake paths at the HTTP layer.
     * Must run before the common SecurityFilterChain (default order 100).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain webSocketSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(pathMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}

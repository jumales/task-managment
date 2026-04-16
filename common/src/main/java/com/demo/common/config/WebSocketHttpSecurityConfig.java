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
 * <p><b>Two-layer security model:</b>
 * <ol>
 *   <li><b>HTTP layer (this class)</b> — {@code permitAll()} on {@code /ws/**} lets the WebSocket
 *       handshake through. A JWT cannot be sent as a standard HTTP header during a browser
 *       WebSocket upgrade (the spec does not allow custom headers), so HTTP-level auth is skipped
 *       intentionally for this path.</li>
 *   <li><b>STOMP layer ({@link WebSocketSecurityConfig})</b> — every {@code CONNECT} frame must
 *       carry a valid {@code Authorization: Bearer <token>} header. The interceptor decodes the JWT
 *       and sets a {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 *       as the STOMP user. Missing or invalid tokens throw a
 *       {@link org.springframework.security.oauth2.jwt.JwtException}.</li>
 * </ol>
 *
 * <p>{@code permitAll()} here does <em>not</em> mean unauthenticated access to data — it only
 * opens the HTTP handshake. All other paths fall through to {@link SecurityConfig} (order 100)
 * which enforces JWT + role checks. CSRF is disabled because WebSocket connections are not
 * vulnerable to CSRF (no cookies; cross-origin JS cannot read WebSocket frames).
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

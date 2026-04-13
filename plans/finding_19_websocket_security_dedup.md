# Finding #19 — Deduplicate WebSocket security config into common module

## Status
UNRESOLVED

## Severity
LOW — identical security-critical code in two services; a bug fix in one must not be missed in the other

## Context
`WebSocketSecurityConfig` and `WebSocketHttpSecurityConfig` are structurally identical in both
`notification-service` and `reporting-service`. The only difference is the HTTP security matcher
path (`/ws/tasks/**` vs `/ws/**`). Per CLAUDE.md: "If the same logic appears twice, extract it."
Security config duplication is particularly risky — a future fix in one copy may be silently missed
in the other.

## Current Duplicated Files
- `notification-service/src/main/java/com/demo/notification/config/WebSocketSecurityConfig.java`
- `notification-service/src/main/java/com/demo/notification/config/WebSocketHttpSecurityConfig.java`
- `reporting-service/src/main/java/com/demo/reporting/config/WebSocketSecurityConfig.java`
- `reporting-service/src/main/java/com/demo/reporting/config/WebSocketHttpSecurityConfig.java`

## Implementation Plan

### 1. Create `common/src/main/java/com/demo/common/config/WebSocketSecurityConfig.java`
Move the STOMP JWT authentication interceptor to common, guarded by `@ConditionalOnClass`:
```java
package com.demo.common.config;

/**
 * Configures STOMP WebSocket security by validating JWT tokens on CONNECT frames.
 * Active only when Spring WebSocket is on the classpath.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer")
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public WebSocketSecurityConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Bean
    public ChannelInterceptor stompAuthInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
                    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                        throw new MessageDeliveryException("Missing or invalid Authorization header");
                    }
                    String token = authHeader.substring(BEARER_PREFIX.length());
                    Jwt jwt = jwtDecoder.decode(token);   // throws JwtException on invalid
                    accessor.setUser(new JwtAuthenticationToken(jwt));
                }
                return message;
            }
        };
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor());
    }
}
```

### 2. Create `common/src/main/java/com/demo/common/config/WebSocketHttpSecurityConfig.java`
Move the HTTP security filter chain with configurable path matcher:
```java
package com.demo.common.config;

/**
 * Permits HTTP-level access to the WebSocket upgrade endpoint (SockJS fallback transports).
 * JWT validation is handled at the STOMP level by WebSocketSecurityConfig.
 * Active only when Spring WebSocket is on the classpath.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer")
public class WebSocketHttpSecurityConfig {

    @Value("${websocket.security.path-matcher:/ws/**}")
    private String pathMatcher;

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
```

### 3. `notification-service/src/main/resources/application.yml`
Add:
```yaml
websocket:
  security:
    path-matcher: /ws/tasks/**
```

### 4. `reporting-service/src/main/resources/application.yml`
Add:
```yaml
websocket:
  security:
    path-matcher: /ws/**
```

### 5. Delete 4 files
- `notification-service/src/main/java/com/demo/notification/config/WebSocketSecurityConfig.java`
- `notification-service/src/main/java/com/demo/notification/config/WebSocketHttpSecurityConfig.java`
- `reporting-service/src/main/java/com/demo/reporting/config/WebSocketSecurityConfig.java`
- `reporting-service/src/main/java/com/demo/reporting/config/WebSocketHttpSecurityConfig.java`

### 6. `common/pom.xml`
Add Spring WebSocket as an optional dependency (so non-WebSocket services don't load it):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
    <optional>true</optional>
</dependency>
```

## Verification
1. Start notification-service — WebSocket at `ws://localhost:{port}/ws/tasks` must require a valid JWT on CONNECT
2. Start reporting-service — WebSocket at `ws://localhost:{port}/ws` must require a valid JWT
3. Connect from `http://localhost:3000` (allowed origin from Finding #8) — should succeed with a valid token
4. Connect without a token — should receive STOMP ERROR frame
5. Run IT tests for both services

## Notes
- Both services already depend on `common` — no pom.xml changes in the service modules needed
- `@ConditionalOnClass` by name (string) avoids a hard compile-time dependency on `spring-websocket` in `common`
- The `JwtDecoder` bean is auto-configured by `spring-boot-starter-oauth2-resource-server` which both services already have
- This change is a prerequisite for Finding #8 (WebSocket origin restriction) — coordinate if implementing both

# Finding #8 — Restrict WebSocket STOMP allowed origins (remove wildcard)

## Status
UNRESOLVED

## Severity
HIGH — WebSocket STOMP endpoint accepts connections from any origin, bypassing gateway CORS policy

## Context
Both `notification-service` and `reporting-service` register their STOMP endpoints with
`setAllowedOriginPatterns("*")`. This allows cross-origin WebSocket connections from any domain.
While the API Gateway enforces CORS for HTTP, the STOMP wildcard bypasses that protection
if services are ever directly reachable (misconfigured network policy, internal access, etc.).

## Root Cause
- `notification-service/src/main/java/com/demo/notification/config/WebSocketConfig.java:30`
  `.setAllowedOriginPatterns("*")`
- `reporting-service/src/main/java/com/demo/reporting/config/WebSocketConfig.java:30`
  `.setAllowedOriginPatterns("*")`

## Files to Modify

### 1. `notification-service/src/main/resources/application.yml`
Add CORS config:
```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:4173}
```

### 2. `reporting-service/src/main/resources/application.yml`
Same:
```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:4173}
```

### 3. `notification-service/src/main/java/com/demo/notification/config/WebSocketConfig.java`
Inject the allowed origins and replace wildcard:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addEndpoint("/ws/tasks")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    // configureMessageBroker unchanged
}
```

### 4. `reporting-service/src/main/java/com/demo/reporting/config/WebSocketConfig.java`
Same change — endpoint is `/ws`, not `/ws/tasks`.

## Verification
1. Start notification-service locally
2. Attempt WebSocket connection from `http://evil.example.com` — should be rejected with 403
3. Attempt connection from `http://localhost:3000` — should succeed
4. Run existing integration tests — should pass unchanged

## Notes
- `CORS_ALLOWED_ORIGINS` env var allows overriding in production (e.g., `https://app.example.com`)
- The comma-separated split must trim whitespace: `allowedOrigins.split("\\s*,\\s*")`
- SockJS fallback transports (XHR, iframe) also respect this origin restriction

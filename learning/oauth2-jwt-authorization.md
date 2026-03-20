# OAuth2 + JWT Authorization for Multi-Client Microservices

## Context

Spring Boot 3.2.5 microservices project with web, mobile, and application clients.
Services: `api-gateway`, `user-service`, `task-service`, `audit-service`.
Existing `Role`, `Right`, `UserRole`, `RoleRight` model in `user-service`.

---

## Decision: OAuth2 Authorization Code + PKCE with Keycloak

### Why this combination

| Requirement | Solution |
|---|---|
| Web client | Authorization Code + PKCE |
| Mobile client | Authorization Code + PKCE (same flow, works natively via system browser) |
| Another application (machine-to-machine) | Client Credentials grant (no user context) |
| Existing Role/Right model | Sync roles/rights into JWT claims via Keycloak mappers |
| API Gateway already exists | Single JWT validation point — services stay lightweight |
| Stateless microservices | JWT is self-contained — no session, no DB lookup per request |

### Why not alternatives

- **Session-based auth** — doesn't scale horizontally, breaks across microservices
- **API keys** — no standard revocation, no user identity, hard to manage per-client
- **Basic auth** — credentials in every request, no token expiry, not suitable for mobile
- **Opaque tokens** — require introspection call on every request (latency); JWT is validated locally

---

## Architecture

```
Web / Mobile / App
       │
       │  Authorization Code + PKCE  →  Keycloak login page
       │  Client Credentials         →  token endpoint directly
       ▼
  Keycloak (port 8180)
  - manages users, clients, realm roles
  - issues JWT with roles + rights claims
  - handles refresh tokens, logout, PKCE
       │
       ▼  Bearer <JWT>
   API Gateway (port 8080)
   - validates JWT signature and expiry
   - rejects unauthenticated/expired requests before routing
   - forwards Authorization header downstream unchanged
       │
       ▼  Authorization header forwarded
  task-service / user-service / audit-service
  - each independently validates JWT (defense in depth)
  - JwtAuthConverter maps JWT claims → Spring Security authorities
  - @PreAuthorize("hasRole('ADMIN')") or hasAuthority('TASK_DELETE')
```

---

## JWT Claims Structure

Keycloak emits standard claims. Configure two custom mappers in the Keycloak client:

```json
{
  "sub": "uuid-of-user",
  "realm_access": {
    "roles": ["ADMIN", "DEVELOPER"]
  },
  "rights": ["TASK_CREATE", "TASK_DELETE", "USER_READ"],
  "email": "alice@demo.com",
  "preferred_username": "alice",
  "exp": 1234567890
}
```

- `realm_access.roles` — standard Keycloak realm roles; mapped to `ROLE_ADMIN`, `ROLE_DEVELOPER`
- `rights` — custom claim added via a Keycloak "User Attribute" protocol mapper; values match the `Right` entity names in `user-service`

---

## Spring Security Setup

### Dependency added to all services

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### application.yml (all services + gateway)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/demo
```

The `issuer-uri` causes Spring to auto-fetch the JWKS endpoint from Keycloak on startup
and cache the public keys used to verify JWT signatures locally — no Keycloak call per request.

### API Gateway — reactive security (WebFlux)

`SecurityConfig` uses `ServerHttpSecurity` (not `HttpSecurity`) because Spring Cloud Gateway
runs on WebFlux. It validates JWT and rejects unauthenticated requests before routing.

### Services — servlet security

`SecurityConfig` uses `HttpSecurity` with `SessionCreationPolicy.STATELESS`.
`JwtAuthConverter` extracts `realm_access.roles` and `rights` custom claim into
Spring Security `GrantedAuthority` objects:
- roles → `SimpleGrantedAuthority("ROLE_ADMIN")`
- rights → `SimpleGrantedAuthority("TASK_CREATE")`

### Method-level authorization

With `@EnableMethodSecurity` on `SecurityConfig`:

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(UUID id) { ... }

@PreAuthorize("hasAuthority('TASK_CREATE')")
public TaskResponse create(TaskRequest request) { ... }
```

---

## Keycloak Setup (docker-compose)

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:24.0
  command: start-dev
  ports:
    - "8180:8080"
  environment:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin
```

Admin UI at `http://localhost:8180`. Steps after first run:
1. Create realm: `demo`
2. Create clients: `web-app` (Authorization Code + PKCE), `mobile-app` (Authorization Code + PKCE), `backend-app` (Client Credentials)
3. Create realm roles matching your `Right` entity names
4. Add custom claim mapper for `rights` (User Attribute → Token Claim, claim name: `rights`)

---

## Key Lessons

### PKCE is required for public clients
Web SPAs and mobile apps cannot safely store a client secret — they are "public clients".
PKCE (Proof Key for Code Exchange) replaces the client secret with a per-request
cryptographic challenge. Always enable it for browser and mobile clients.

### Gateway validates; services also validate (defense in depth)
Even though the gateway rejects bad tokens, services independently validate the JWT.
This means a request that bypasses the gateway (internal call, misconfiguration) is still rejected.
The cost is low — JWT validation is local (no network call) using the cached JWKS keys.

### issuer-uri triggers JWKS auto-discovery
Setting `spring.security.oauth2.resourceserver.jwt.issuer-uri` causes Spring to call
`<issuer>/.well-known/openid-configuration` on startup, retrieve the `jwks_uri`, and
cache the public keys. Token validation is then fully local — no round-trip to Keycloak per request.
If Keycloak is down at startup, the service will fail to start. Use `jwk-set-uri` directly
if you want to decouple startup from Keycloak availability.

### Roles vs Rights in JWT
Keycloak natively supports realm/client roles. Custom rights require a protocol mapper
configured in the Keycloak admin console to inject a user attribute into the JWT as a
custom claim. The `JwtAuthConverter` must then read this custom claim explicitly —
Spring's default converter only reads scopes and roles from standard claim paths.

# Feign Client JWT Forwarding Issue

## Context

Spring Boot 3.2.5 microservices with OAuth2 JWT security enabled on all services.
`task-service` calls `user-service` via a Feign client to resolve user details during
task creation and response mapping.

---

## Symptom

After enabling `spring-boot-starter-oauth2-resource-server` on all services, task creation
started returning HTTP 500. The root cause was a 401 from `user-service` when called
internally by `task-service` via Feign.

```
Browser → POST /api/v1/tasks → API Gateway (JWT valid ✓) → task-service
  task-service → GET /api/v1/users/{id} via Feign → user-service (401 ✗)
```

---

## Root Cause

Each service independently validates JWTs (defense in depth). The API Gateway validates
the token and forwards the `Authorization` header downstream to `task-service`. However,
Feign does **not** automatically propagate headers — its outgoing requests had no
`Authorization` header, so `user-service` rejected them with 401.

---

## Fix

Add a `RequestInterceptor` bean to any service that makes Feign calls. It reads the
`Authorization` header from the current incoming servlet request and copies it into
every outgoing Feign request.

```java
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;

        String authHeader = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null) {
            template.header(AUTHORIZATION_HEADER, authHeader);
        }
    }
}
```

Spring auto-detects all `RequestInterceptor` beans and applies them to every Feign client
in the same application context — no extra configuration needed.

---

## Key Lessons

### Every service-to-service call needs the JWT
When all services are resource servers, internal Feign calls are subject to the same JWT
validation as external calls. The token must be explicitly forwarded — it is not propagated
automatically.

### RequestContextHolder can be null
`RequestContextHolder.getRequestAttributes()` returns null when there is no active servlet
request (e.g. scheduled jobs, Kafka consumers, async threads). Always null-check before
reading headers to avoid `NullPointerException` in those contexts.

### Applies to all services that use Feign
Any service that calls another service via Feign needs this interceptor once JWT security
is enabled. Add `FeignAuthInterceptor` to each such service's `config` package.

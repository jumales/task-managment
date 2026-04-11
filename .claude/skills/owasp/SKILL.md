---
name: owasp
description: Perform an OWASP Top 10 (2021) security audit of the full stack — broken access control, cryptographic failures, injection, insecure design, security misconfiguration, vulnerable components, auth failures, integrity failures, logging gaps, and SSRF.
---
Perform a security audit of this codebase mapped to the OWASP Top 10 (2021). This project is a Spring Boot microservices backend with a React frontend. Use the knowledge below about the architecture to focus your analysis on areas that matter most.

## Architecture context

- **Backend**: Java 17, Spring Boot 3.2.5, Spring Security OAuth2 Resource Server, Keycloak JWT
- **Frontend**: React 18, TypeScript, Axios, Keycloak-JS
- **Data**: PostgreSQL (Spring Data JPA, soft deletes via `@SQLRestriction`), MinIO (file storage)
- **Messaging**: Kafka (outbox pattern)
- **Services**: eureka-server, api-gateway, user-service, task-service, audit-service, file-service

## What to check for each OWASP category

### A01 — Broken Access Control
- Controllers missing `@PreAuthorize` annotations (all authenticated users treated equally)
- Admin-only endpoints (user CRUD, role/right management) accessible to any role
- Audit endpoints accessible to any authenticated user (can read any task's history)
- IDOR: can a user access/modify resources that belong to another user?
- Check every `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` across all controllers for authorization annotations
- Look for `@EnableMethodSecurity` in SecurityConfig classes

### A02 — Cryptographic Failures
- Secrets or credentials hardcoded in `application.yml` (DB passwords, MinIO keys)
- JWT `issuer-uri` using HTTP instead of HTTPS in non-dev configs
- Presigned MinIO URLs: check expiry time and whether deleted files' URLs are invalidated
- Token storage in frontend: check for localStorage/sessionStorage usage of tokens

### A03 — Injection
- JPQL/SQL queries: look for string concatenation in `@Query` annotations
- Native queries: any `nativeQuery = true` with user input
- File upload: original filename used in storage path construction without sanitization
- Feign clients: any path variables built from user input without encoding

### A04 — Insecure Design
- Rate limiting: any throttling on file upload, login, or public endpoints?
- Business logic: can a task be deleted while it has active comments? (check `RelatedEntityActiveException` guards)
- Soft delete bypass: can deleted records be accessed by manipulating query params?

### A05 — Security Misconfiguration
- CORS: `allowedHeaders("*")` or `allowedOrigins("*")` in SecurityConfig
- CSRF disabled — is this appropriate? (yes for stateless JWT, but verify)
- Spring Boot actuator endpoints: are `/actuator/**` endpoints exposed without auth?
- Error responses: do they leak stack traces, SQL details, or internal paths?
- Docker/MinIO/Keycloak: default credentials in docker-compose.yml

### A06 — Vulnerable and Outdated Components
- Check `pom.xml` (parent Spring Boot version, Spring Cloud version, MinIO SDK)
- Check `web-client/package.json` (React, Axios, Keycloak-JS versions)
- Flag any dependency that is more than 1 major version behind latest

### A07 — Identification and Authentication Failures
- JWT validation: is the signature verified? Is `issuerUri` configured on every service?
- Token refresh: Axios refreshes token on every request — could cause refresh storms?
- Session management: any server-side session state despite `STATELESS` policy?
- Service-to-service auth via Feign: does the JWT forward correctly? Check `FeignAuthInterceptor`

### A08 — Software and Data Integrity Failures
- Outbox pattern: are outbox events validated before publishing to Kafka?
- Flyway migrations: are migration files checksummed and immutable?
- Dependency integrity: any direct file downloads or non-Maven-Central dependencies?

### A09 — Security Logging and Monitoring Failures
- `ControllerLoggingAspect`: are sensitive parameters (`password`, `token`, `secret`) masked?
- `MdcFilter`: is `userId` injected into every log line for audit trail?
- Are failed auth attempts logged?
- Are 4xx/5xx errors logged with enough context to detect attacks?

### A10 — Server-Side Request Forgery (SSRF)
- Any endpoint that accepts a URL and fetches it server-side?
- MinIO presigned URL generation: is the bucket/object key validated before generating URLs?
- Feign clients: are service URLs derived from user input anywhere?

---

## Instructions

1. Read each relevant file — do not guess; check the actual code.
2. For each OWASP category, report:
   - **Status**: PASS / FAIL / WARN
   - **Finding**: what was found (or confirmed safe)
   - **File**: exact file path and line number
   - **Recommendation**: specific fix if FAIL or WARN
3. Prioritize FAIL items — these need immediate attention.
4. At the end, produce a **risk-ranked action list** ordered: FAIL → WARN → PASS.
5. For every FAIL, suggest the exact code change needed (Java annotation, config property, etc.).

Start with A01 (Broken Access Control) as it has the most known gaps in this codebase.

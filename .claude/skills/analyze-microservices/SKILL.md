---
name: analyze-microservices
description: Perform a structured microservice architecture review across all 11 principles (bounded context, DB-per-service, gateway, Kafka outbox, resilience, observability, etc.) and produce a rated findings report.
---
Perform a structured review of the project against microservice architecture principles.
Produce a report with findings grouped by principle, each rated **VIOLATION** (breaks the principle), **WARNING** (risk or smell), or **SUGGESTION** (improvement opportunity).

---

## How to run the analysis

Work through each principle below in order. For every principle:
1. Read the relevant files (pom.xml files, application.yml files, entity/service/controller/repository classes, docker-compose.yml, gateway config).
2. Collect concrete evidence — file paths and line numbers for each finding.
3. Record findings using the format at the bottom of this file.

Do not guess — only report findings backed by code you have read.

---

## Principles to check

### 1. Single Responsibility / Bounded Context
Each service must own one clearly scoped domain. It must not contain logic or entities that belong to another service's domain.

Check:
- Read every `model/` package across all services. Do any entities clearly belong to a different service's domain?
- Read every `service/` class. Does any service method orchestrate business logic that spans multiple domains?
- Does the service name match the domain it actually manages?

Flag if: a service contains entities or business rules from more than one domain.

---

### 2. Database per Service
Each service must have its own isolated database. No two services may share a schema or connect to the same datasource URL.

Check:
- Read every `application.yml`. Compare all `spring.datasource.url` values — are any two services pointing at the same database name?
- Read `docker-compose.yml`. Does every service have its own dedicated Postgres container?
- Search for any `@Entity` class in service A that is also imported or referenced by service B.

Flag if: two services share a database URL, or one service queries another service's tables directly.

---

### 3. API Gateway as Single Entry Point
All external traffic must enter through the gateway. Services must not expose their ports directly.

Check:
- Read `api-gateway/src/main/resources/application.yml`. List all registered route path prefixes.
- Read every service's `controller/` package and collect all `@RequestMapping` base paths.
- Compare: is every controller base path covered by a gateway route? List any missing ones.
- Read `docker-compose.yml`. Do any service containers have a `ports:` block that maps to the host? Only the gateway should expose a host port.

Flag if: a controller path has no matching gateway route, or a service exposes a host port directly.

---

### 4. Inter-Service Communication
Services must not call each other's databases directly. Synchronous calls (Feign) are acceptable for real-time data needs; asynchronous events (Kafka) are preferred for state changes that do not require an immediate response.

Check:
- Search for `@FeignClient` across all services. For each client: is the call truly synchronous/real-time (justified), or could it be replaced with an event?
- Search for any `JdbcTemplate`, `EntityManager`, or datasource bean that references another service's database.
- For every Feign call that happens inside a `@Transactional` method, flag it — a remote call inside a transaction can cause distributed transaction issues.

Flag if: a service queries another service's database directly, or a Feign call is made inside a transaction boundary.

---

### 5. Loose Coupling via Events (Outbox Pattern)
State changes with side effects on other services must be communicated asynchronously via the outbox pattern, not inline Kafka sends or direct calls.

Check:
- Search for `KafkaTemplate.send(` across all services. Any direct `send` outside of an `@Scheduled` outbox publisher is a violation — the event may be lost if the transaction rolls back.
- Read every `OutboxPublisher` or equivalent. Is it the only place that calls `KafkaTemplate.send`?
- Read every `@Transactional` method that writes business state. If it also publishes to Kafka directly, flag it.

Flag if: `KafkaTemplate.send` is called inside a `@Transactional` business method (outside the outbox publisher).

---

### 6. Resilience — Failure Isolation
A failure in one service must not cascade to others. Feign clients must have timeouts or fallbacks; callers must handle downstream unavailability gracefully.

Check:
- Search for `@FeignClient`. Does each client have a fallback or is the error caught at the call site?
- Search for `try { ... } catch` blocks around Feign calls. Is the catch block intentional (graceful degradation) or silently swallowing errors?
- Read `application.yml` files for `feign.client.config`, `connect-timeout`, `read-timeout` settings.
- Search for `@CircuitBreaker` or Resilience4j annotations.

Flag if: a Feign call has no timeout configured and no fallback handling; flag as SUGGESTION if timeouts exist but no circuit breaker.

---

### 7. Externalized Configuration
No environment-specific values (hostnames, ports, credentials, topic names) may be hardcoded in Java source files.

Check:
- Search all Java files for hardcoded strings that look like: hostnames (`localhost`, IP addresses), port numbers inside strings, database names, Kafka topic names defined as plain string literals outside of a `@Value`-injected constant or `application.yml`.
- Read `application.yml` files — are credentials (`password`, `secret`) committed in plain text? They should reference environment variables (`${ENV_VAR}`).

Flag if: a hostname, credential, or topic name is hardcoded in a `.java` file or committed as plaintext in a config file.

---

### 8. Shared Code Discipline (Common Module)
The `common` module must only contain code genuinely shared across 2+ services: DTOs, events, and exceptions. It must not contain business logic, entity classes, or service-layer code.

Check:
- Read every file under `common/src/main/java/`. Categorise each as: DTO, event, exception, or **other**.
- Flag any `@Entity`, `@Service`, `@Repository`, or `@Component` in the common module — these belong in a specific service.
- Flag any business logic (conditional branching, calculations) in common DTO/event classes beyond simple factory methods.
- Check each DTO/event class: is it actually imported by 2+ services? If only one service uses it, flag as WARNING (unnecessary coupling).

Flag if: the common module contains entities, services, or repositories.

---

### 9. Observability
Each service must produce enough signal to diagnose problems in production.

Check:
- Search for `LoggerFactory.getLogger` or `@Slf4j`. Are loggers present in service and consumer classes? Are they absent from any class that performs I/O or state changes?
- Read `application.yml` files for `management.endpoints`, `logging.level` configuration.
- Search for structured log messages in Kafka consumers — is each consumed event logged with `taskId` / correlation identifiers?
- Check if `spring-boot-starter-actuator` is present in any `pom.xml`.

Flag if: a Kafka consumer or service class has no logging; flag as SUGGESTION if actuator is absent.

---

### 10. API Design Consistency
All REST endpoints must follow consistent conventions across services.

Check:
- Read every `@RestController`. Verify:
  - HTTP method matches semantics (`GET` = read, `POST` = create, `PUT` = full update, `DELETE` = remove).
  - Create endpoints return `201 Created` (`@ResponseStatus(HttpStatus.CREATED)`).
  - Delete endpoints return `204 No Content`.
  - IDs are UUIDs, not sequential integers.
  - Path variables use plural nouns (`/api/tasks`, not `/api/task`).
- Read every controller for missing `@Operation` / `@ApiResponse` OpenAPI annotations.

Flag if: wrong HTTP status codes are returned, or non-UUID IDs are used; flag as WARNING for missing OpenAPI docs.

---

### 11. Test Coverage for Integration Points
Every Kafka consumer and every Feign-backed flow must have at least one Testcontainers integration test.

Check:
- List all `@KafkaListener` methods across all services.
- List all `@FeignClient` interfaces across all services.
- Search the test directories for `*IT.java` files. For each listener and Feign client, is there a matching IT test?
- Check that IT tests use `@Container @ServiceConnection` (not manually wired URLs) and `Awaitility` for async assertions.

Flag if: a `@KafkaListener` or `@FeignClient` has no corresponding integration test.

---

## Report format

Produce the report in this structure:

```
## Microservice Architecture Analysis Report

### Summary
| Principle | Violations | Warnings | Suggestions |
|---|---|---|---|
| 1. Single Responsibility     | n | n | n |
| 2. Database per Service      | n | n | n |
| ...                          |   |   |   |
| **Total**                    | n | n | n |

---

### Findings

#### [VIOLATION | WARNING | SUGGESTION] <Short title>
- **Principle:** <number and name>
- **Location:** `path/to/File.java:line`
- **Evidence:** <exact quote or description of what was found>
- **Why it matters:** <one sentence on the risk>
- **Recommendation:** <concrete fix>

---
(repeat for each finding)

### What looks good
List principles where no issues were found, with a one-line note on what was checked.
```

Prioritise findings: list all VIOLATIONs first, then WARNINGs, then SUGGESTIONs within each principle section.

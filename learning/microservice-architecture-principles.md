# Microservice Architecture Principles

A reference mapping the 11 principles implemented in this project, plus the 7 that are missing or incomplete.

---

## CAP Theorem — AP vs CP

CAP states a distributed system can guarantee only **2 of 3**:

| Letter | Property | Meaning |
|---|---|---|
| **C** | Consistency | Every read gets the most recent write (or an error) |
| **A** | Availability | Every request gets a response (no errors, no timeouts) |
| **P** | Partition Tolerance | System keeps working when network between nodes breaks |

**P is not optional** — networks fail, so the real choice is always **CP vs AP**.

- **CP** — refuses to answer rather than return stale data ("I'd rather error than lie")
- **AP** — still responds but data may be stale ("I'd rather return old data than error")

### Where this project sits

| Component | Choice | Why |
|---|---|---|
| `task-service` PostgreSQL | **CP** | Single source of truth, optimistic locking (`@Version`) |
| `search-service` Elasticsearch | **AP** | Stale reads acceptable — search is not authoritative |
| `reporting-service` projections | **AP** | Reports lag ~1s, fine for analytics |
| `audit-service` | **AP** | Audit log may be 1s behind but never wrong — events are immutable |
| Eureka service registry | **AP** | Keeps serving stale registry data during partition rather than going down |

The outbox's `published = false` flag is the consistency recovery mechanism — if Kafka is down,
events queue in PostgreSQL and publish when Kafka recovers (self-healing toward consistency).

---

## Principles IN USE

### 1. Single Responsibility / Bounded Context

Each service owns one domain and nothing else.

| Service | Domain |
|---|---|
| `task-service` | Tasks, projects, phases, work logs |
| `user-service` | Identity, Keycloak integration |
| `file-service` | File upload/download, MinIO |
| `audit-service` | Immutable change history (read-only, no write endpoints) |
| `search-service` | Full-text search, Elasticsearch |
| `notification-service` | Emails + WebSocket push |
| `reporting-service` | Analytics projections |

### 2. Database per Service

No service shares a schema with another.

```
task-service   → task_db     (PostgreSQL, Flyway migrations)
audit-service  → audit_db    (append-only tables)
search-service → Elasticsearch (not PostgreSQL at all)
file-service   → file_db
```

All services set `spring.jpa.hibernate.ddl-auto: validate` — fail on schema mismatch.
Trade-off: no SQL JOINs across services — must use API calls or events.

### 3. API Gateway

All external traffic enters through one door (`api-gateway:8080`).

Handles:
- JWT validation (before traffic reaches any service)
- Rate limiting via Redis token bucket (file uploads capped at 10 req/min)
- CORS headers
- Dynamic routing via Eureka (`lb://service-name`)

### 4. Service Discovery (Eureka HA)

All services use `server.port: 0` (random port at startup). Eureka tracks the real port.
Two Eureka peers (`peer1:8761`, `peer2:8762`) replicate each other for high availability.

### 5. Circuit Breaker (Resilience4j)

Task-service wraps Feign calls to user-service in a circuit breaker:
- Failure threshold: 50% of last 20 calls
- If open: returns null/empty — task still loads, just without user name
- Wait 10s before retrying downstream

The batch `getUsersByIds` call exists specifically to avoid N+1 queries — one Feign call
for an entire task list instead of one per task.

### 6. Event-Driven Communication — Transactional Outbox Pattern

Problem: if task-service saves a task and crashes before publishing to Kafka, the event is lost.

Solution:
```
Task mutation → DB transaction:
  ├── UPDATE tasks SET ...
  └── INSERT INTO outbox_events (topic, payload, published=false)

OutboxPublisher (every 1s):
  ├── SELECT * FROM outbox_events WHERE published = false
  ├── Send to Kafka (parallel)
  └── UPDATE outbox_events SET published = true  ← only after delivery confirmed
```

Three Kafka topics:
- `task-changed` → audit, notification, reporting
- `task-events`  → search, reporting
- `user-events`  → search

All consumers use `MANUAL_IMMEDIATE` ACK — only acknowledge after successfully writing to their own DB.

### 7. CQRS (Command/Query Responsibility Segregation)

```
Write path:  POST /api/v1/tasks  → task-service → PostgreSQL + outbox
Read path:   GET  /api/v1/search → search-service → Elasticsearch
Read path:   GET  /api/v1/reports → reporting-service → denormalized PostgreSQL projections
```

Each read model is optimized for its use case — Elasticsearch for full-text, reporting DB for aggregations.

### 8. Shared Common Module

Pragmatic compromise: `common` holds DTOs, events, security config, and exceptions used by 2+ services.

```
common/
├── config/SecurityConfig.java       ← JWT validation, identical for all services
├── config/KafkaTopics.java          ← topic name constants (no magic strings)
├── event/TaskChangedEvent.java      ← 15 change types, single event shape
├── exception/GlobalExceptionHandler.java
└── dto/ (40+ shared DTOs)
```

Trade-off: all services redeploy together for any `common` change.

### 9. Observability — Logs, Metrics, Tracing

```
Logs    → JSON (Logstash Logback) → Logstash TCP:5000 → Kibana
Metrics → Micrometer → /actuator/prometheus → Prometheus → Grafana
Tracing → Micrometer Tracing (Brave) → traceId/spanId injected into every log line via MdcFilter
```

`MdcFilter.java` injects `traceId` into MDC. Every log line across every service carries the same
traceId — follow one request through gateway → task-service → user-service in Kibana.

### 10. Defense in Depth — Auth at Every Layer

JWT is validated twice per request:
```
Request → api-gateway (validates JWT signature via JWKS cache)
        → task-service (also validates JWT via common/SecurityConfig.java)
```

Inter-service Feign calls carry the JWT via `FeignAuthInterceptor.java`. Even internal calls are authenticated.

### 11. Health Checks + Actuator

All services expose:
- `/actuator/health` — Docker healthcheck liveness probe
- `/actuator/prometheus` — Prometheus metrics scraping
- `/v3/api-docs` — OpenAPI 3.0 specification (springdoc-openapi)

---

## Principles NOT IN USE

### ❌ 1. Dead Letter Queue (DLQ) — HIGH RISK

When a Kafka consumer throws an exception, the message is redelivered forever — blocking the partition.
A malformed event or DB outage causes an infinite retry loop; newer messages are never processed.

**Fix:** Spring Kafka `DeadLetterPublishingRecoverer` + `DefaultErrorHandler` with exponential backoff:
3 retries, then park to `topic-name.DLT`. Zero code in any service handles this today.

### ❌ 2. Idempotent Consumers — HIGH RISK

If Kafka delivers the same message twice (network retry, consumer rebalance), consumers process it twice:
- `audit-service` → two identical audit rows
- `notification-service` → two emails sent to the user

No deduplication exists. `OutboxEvent` has no stable `messageId` field that consumers could key on.

**Fix:** Store processed `messageId` in DB per consumer; skip if already seen.

### ❌ 3. Externalized / Centralized Configuration — MEDIUM

Each service has its own `application.yml`. The Kafka bootstrap server address appears in 6 separate files.
Migrating Kafka to a new host requires editing 6 files and redeploying 6 services.

**Fix:** Spring Cloud Config Server — all services pull config at startup from one place.
Adding Zipkin is a 3-line docker-compose change + one property per service.

### ❌ 4. Distributed Trace Backend — LOW (easy win)

`traceId`/`spanId` appear in Kibana log lines, but no visual flame graph of the request chain exists.
Cannot see which service in a chain caused a timeout.

**Fix:** Add Zipkin container to docker-compose + set `management.zipkin.tracing.endpoint`.
Existing Micrometer Tracing automatically starts exporting spans — zero code changes in any service.

### ❌ 5. Bulkhead Pattern — MEDIUM

All Feign calls share the same thread pool. If user-service hangs with 30s timeouts, all Feign threads
in task-service are consumed — task-service becomes unresponsive even for requests that don't need user-service.

**Fix:** Resilience4j `@Bulkhead(name = "userService", type = Bulkhead.Type.THREADPOOL)` on `UserClientHelper`.

### ❌ 6. Saga with Compensating Transactions — LOW (current flows don't need it)

Multi-step distributed operations have no rollback strategy. If a downstream service fails partway,
the upstream write is not undone. Current flows are fire-and-forget (outbox → consumers), so this
is acceptable now but becomes critical if transactional multi-service workflows are added.

### ❌ 7. mTLS / Service Mesh — LOW for dev, HIGH for prod

`FeignAuthInterceptor` passes the user's JWT between services — authenticates the *user*, not the
*calling service*. A compromised internal service could call any other service as any user.

**Fix:** Istio or Linkerd service mesh with mTLS + network policies that restrict which services
can talk to which.

---

## Priority Fix Order

| Gap | Severity | Effort |
|---|---|---|
| **DLQ** | High — silent message loss possible | Low — Spring Kafka config only |
| **Idempotent consumers** | High — duplicate emails/audit rows | Medium — add `processed_messages` table |
| **Bulkhead** | Medium — cascading slowness risk | Low — one Resilience4j annotation |
| **Centralized config** | Medium — operational pain at scale | Medium — add Spring Cloud Config server |
| **Distributed trace backend** | Low — Kibana covers basics | Very low — add Zipkin container |
| **Saga / compensation** | Low — current flows don't need it | High — architectural change |
| **mTLS / service mesh** | Low for dev, High for prod | High — Istio/Linkerd |

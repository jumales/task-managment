# Logging in Microservices

## Why plain-text logging breaks in distributed systems

A monolith writes to one log file. A microservice system has dozens of instances, each writing its own
stream. When a single user request fans out across task-service → user-service → audit-service, the logs
for that request are split across three files, interleaved with unrelated traffic.

Without structure and correlation, debugging requires grepping three log streams simultaneously and hoping
the timestamps line up. Two things fix this: **structured logging** (so logs are queryable) and
**trace IDs** (so one request can be followed end-to-end).

---

## 1. Structured JSON logging

`show-sql: true` and plain-text Logback output both produce logs that humans can read but machines
cannot efficiently index. ELK (Elasticsearch + Logstash + Kibana) ingests JSON — one object per line —
and indexes every field.

**How it works here:**
- `logstash-logback-encoder` replaces Logback's default pattern encoder with a JSON encoder
- `logback-spring.xml` configures a `ConsoleAppender` with `LogstashEncoder`
- Every log line becomes a JSON object: `level`, `message`, `logger`, `timestamp`, `service`, plus any MDC fields

```json
{
  "@timestamp": "2026-03-21T14:05:01.123Z",
  "level": "DEBUG",
  "logger_name": "com.demo.common.web.ControllerLoggingAspect",
  "message": "[TaskController] create() params: {request=TaskRequest(...)}",
  "service": "task-service",
  "traceId": "3fa85f64a5c1f9c8",
  "spanId": "a1b2c3d4",
  "requestId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "method": "POST",
  "path": "/api/v1/tasks"
}
```

**Key lesson:** `show-sql: true` prints SQL to stdout always, at INFO level, with no field structure.
Replace it with `show-sql: false` + `logging.level.org.hibernate.SQL: DEBUG` — SQL is then opt-in
and correctly structured in the JSON output.

---

## 2. Trace ID propagation (Micrometer Tracing)

When task-service calls user-service via Feign, the two calls are causally related but produce
independent logs. Without a shared ID, you cannot link them.

**How it works here:**
- `micrometer-tracing-bridge-brave` is added to `common`
- Spring Boot auto-configures Brave tracing when it finds the bridge on the classpath
- Brave generates a `traceId` for the first service that receives a request and propagates it via
  the `traceparent` HTTP header (W3C Trace Context standard)
- Downstream Feign calls forward the header automatically
- Micrometer puts `traceId` and `spanId` into the SLF4J MDC — so they appear in every log line

Result: all logs for one user request, across all services, share the same `traceId`.

**Sampling:** `management.tracing.sampling.probability: 1.0` means every request is traced.
Lower this in production (e.g. `0.1` = 10%) to reduce overhead.

---

## 3. MDC enrichment (MdcFilter)

`traceId` links logs across services. MDC enrichment adds per-request context within a service.

`MdcFilter` (in `common`) runs after Spring Security on every request and writes to MDC:

| Field | Value | Purpose |
|---|---|---|
| `requestId` | UUID per request | Correlate logs within one service instance |
| `method` | GET / POST / … | Filter by HTTP method in Kibana |
| `path` | /api/v1/tasks | Filter by endpoint |
| `userId` | JWT subject | Filter all logs for one user |

**Key lesson:** MDC must be cleared in a `finally` block. Thread pools reuse threads — without
cleanup, the next request on the same thread inherits the previous request's MDC values.

```java
try {
    MDC.put("requestId", UUID.randomUUID().toString());
    chain.doFilter(request, response);
} finally {
    MDC.clear(); // always, even on exception
}
```

**Filter ordering:** `MdcFilter` is annotated `@Component`, which registers it at
`Ordered.LOWEST_PRECEDENCE`. Spring Security runs at order -100, so it runs first — meaning the
JWT is already processed and `SecurityContextHolder` is populated by the time `MdcFilter` reads
the userId. No explicit ordering configuration is needed.

---

## 4. ELK stack

ELK = **E**lasticsearch + **L**ogstash + **K**ibana.

```
Service (JSON logs via TCP)
    └─→ Logstash (parse + route)
            └─→ Elasticsearch (index + store)
                    └─→ Kibana (query + visualise)
```

**How it works here:**
- Services log JSON to stdout always (useful locally and in Docker)
- When running with `spring.profiles.active=logstash`, services also ship logs directly to
  Logstash on port 5000 via TCP (`LogstashTcpSocketAppender`)
- Logstash pipeline (`docker/logstash/pipeline/logstash.conf`) receives the JSON and writes to
  Elasticsearch, one index per service per day: `task-service-2026.03.21`
- Kibana at `http://localhost:5601` connects to Elasticsearch for querying

**Useful Kibana queries:**
```
# All logs for one trace
traceId: "3fa85f64a5c1f9c8"

# All errors for one user today
level: "ERROR" AND userId: "9b1deb4d-..."

# Slow endpoints
service: "task-service" AND level: "DEBUG" AND path: "/api/v1/tasks"
```

**Index pattern in Kibana:** create `*-service-*` to query all services at once.

---

## Key lessons summary

- Never use `show-sql: true` — it always logs at INFO and produces unstructured output; use `logging.level.org.hibernate.SQL: DEBUG` instead
- Structured JSON is not optional in microservices — it is the prerequisite for any log aggregation
- `traceId` must be propagated via HTTP headers; Micrometer Tracing does this automatically
- MDC must always be cleared in `finally` — thread-pool reuse makes leaking MDC values a real bug
- Log levels belong in `application.yml`, not hardcoded in `logback-spring.xml` — this allows per-environment overrides without rebuilding
- `management.tracing.sampling.probability` should be `1.0` in dev and `0.05`–`0.1` in prod

# Design Patterns — Pros, Cons & Potential Issues

Analysis of every architectural and design pattern used in this project, with real tradeoffs and specific risks to watch for.

---

## 1. Transactional Outbox

**What it solves:** "dual write problem" — you can't atomically write to DB and publish to Kafka in one transaction.

| | |
|---|---|
| **+** | Guaranteed delivery — event survives Kafka downtime |
| **+** | Event and state change are atomic (same DB transaction) |
| **+** | No distributed transaction (2PC) needed |
| **-** | Extra DB table + polling loop overhead |
| **-** | Adds latency (poll interval = min delay before publish) |
| **-** | Poller is a single point of failure if not clustered |

**Potential issues in this project:**
- `OutboxPublisher` uses `@Scheduled(fixedDelay=1000)` — if multiple instances run (horizontal scale), **multiple pods will poll and publish the same events**. Fix: verify `findUnpublishedForUpdate()` uses `SELECT ... FOR UPDATE SKIP LOCKED`.
- No TTL/cleanup on published outbox rows → table grows unbounded. Need a cleanup job.
- If Kafka send succeeds but `published=true` update fails → event published twice. Consumers must be idempotent (they are — see pattern #4).

---

## 2. Choreography Saga

**What it solves:** multi-service workflows without a central coordinator.

| | |
|---|---|
| **+** | Loose coupling — services don't know about each other |
| **+** | Easy to add new consumers without touching producers |
| **+** | No single point of failure (no orchestrator) |
| **-** | Hard to trace a full "saga" across services — where did it fail? |
| **-** | No rollback mechanism built-in — compensating transactions must be added manually |
| **-** | Business logic scattered across services — hard to see the full flow |

**Potential issues in this project:**
- If `notification-service` fails after `audit-service` succeeds → **partial saga**, no rollback. Audit has the record, user never got the email.
- No saga correlation ID visible in the codebase — distributed tracing is configured, but saga-level correlation requires explicit saga ID propagation through events.
- Adding a new consumer service is easy now, but **consumer count grows silently** — the producer has no visibility into downstream dependents.

---

## 3. Ports & Adapters (Hexagonal Architecture)

**What it solves:** decouples business logic from infrastructure (Kafka, Keycloak, DB).

| | |
|---|---|
| **+** | Domain logic testable without real Kafka/Keycloak |
| **+** | Can swap Kafka for RabbitMQ by changing one class |
| **+** | Clear contract — interface documents what service needs |
| **-** | More files/indirection for simple cases |
| **-** | Only partially applied — `task-service` doesn't use ports, talks to repos directly |

**Potential issues in this project:**
- Inconsistency — `user-service` has `KeycloakUserPort` and `UserEventPublisherPort` but `task-service` has no port layer. Makes architecture harder to reason about uniformly.
- If a port has only one implementation ever, the interface adds noise without value. Evaluate whether `KeycloakUserPort` will ever have a second impl.

---

## 4. Idempotent Consumer

**What it solves:** Kafka delivers *at least once* — without dedup, event can be processed twice.

| | |
|---|---|
| **+** | Exactly-once processing semantics on the consumer side |
| **+** | Simple implementation — unique constraint does the work |
| **+** | Works across restarts (persisted, not in-memory) |
| **-** | `processed_kafka_events` table grows forever |
| **-** | Every message costs an extra DB write (even duplicates) |
| **-** | `REQUIRES_NEW` propagation = separate transaction = possible deadlock under high concurrency |

**Potential issues in this project:**
- No TTL/purge on `processed_kafka_events` — in high-throughput scenarios this becomes a hot table. Add a cleanup job deleting rows older than Kafka retention period (e.g., 7 days).
- `DataIntegrityViolationException` catch is broad — also catches FK violations or other constraint failures, silently swallowing them as "duplicates". Should be narrowed to the specific unique constraint.

---

## 5. Dead Letter Queue + Exponential Backoff

**What it solves:** transient failures shouldn't drop messages.

| | |
|---|---|
| **+** | Transient errors auto-retry without manual intervention |
| **+** | Permanent failures (bad payload) go to DLQ for inspection |
| **+** | DLQ messages can be replayed after a bug fix |
| **-** | DLQ requires monitoring — if nobody watches it, failures are silent |
| **-** | Retry amplifies load during outages (thundering herd) |
| **-** | Max 3 attempts may be too few for some transient failures |

**Potential issues in this project:**
- No DLQ consumer or alerting visible in the codebase. DLQ messages pile up silently — need Prometheus metric or alert.
- `addNotRetryableExceptions(DeserializationException.class)` is correct but incomplete — `ResourceNotFoundException` should also be non-retryable (retrying won't fix a missing entity).
- DLQ topic naming follows Kafka convention (`{original-topic}.DLT`) — ensure those topics have a different retention policy, not deleted after 7 days like normal topics.

---

## 6. Pessimistic + Optimistic Locking

**What it solves:** concurrent updates to the same task.

| | |
|---|---|
| **+** | Pessimistic: guarantees no concurrent modification — safe for critical sections |
| **+** | Optimistic: no lock held → higher throughput for read-heavy flows |
| **+** | `@Version` gives clients a conflict signal (HTTP 409) to retry |
| **-** | Pessimistic lock held for full transaction duration → DB connection pressure under load |
| **-** | Using BOTH on the same entity is redundant and confusing |
| **-** | Pessimistic lock on Postgres escalates to row-level lock — can cause deadlocks if two transactions lock in different order |

**Potential issues in this project:**
- Having both pessimistic (`findByIdForUpdate`) and optimistic (`@Version`) locking on `Task` is contradictory. Pessimistic locking already prevents concurrent updates — the `@Version` check inside the locked block is redundant. Pick one strategy per use case.
- Long transactions with pessimistic lock + Feign calls to `user-service` inside = **lock held during network call**. If user-service is slow, the lock blocks other writers for the full Feign timeout.

---

## 7. AOP Logging (ControllerLoggingAspect)

**What it solves:** cross-cutting concern — log all controller inputs without touching controller code.

| | |
|---|---|
| **+** | Zero controller code needed — automatically applies to all `@RestController` |
| **+** | Centralized sensitive field masking |
| **+** | Easy to enable/disable per service via log level |
| **-** | `@Before` on every controller method = reflection on every request |
| **-** | If a parameter is a large object (e.g., file upload metadata), it gets serialized for logging |
| **-** | Spring AOP uses proxies — won't intercept self-calls or non-Spring beans |

**Potential issues in this project:**
- Masking is done by parameter *name* (`isSensitive(paramName)`). If someone names a password field `pwd` instead of `password`, it won't be masked — fragile. Consider annotation-based masking (`@Sensitive`) instead.
- `log.debug("...", params.toString())` — the `toString()` is always constructed even when DEBUG is off. Use lambda form: `log.debug("...", () -> params)` to avoid construction cost.

---

## 8. MDC Filter Chain

**What it solves:** structured logging — every log line automatically has `requestId`, `userId`, `method`, `path`.

| | |
|---|---|
| **+** | Zero logging code in business layer |
| **+** | `requestId` enables tracing a single request across all log lines |
| **+** | `MDC.clear()` in `finally` prevents context bleed between requests |
| **-** | MDC is ThreadLocal — breaks with `@Async` methods |
| **-** | Doesn't propagate across service boundaries (only within one service) |

**Potential issues in this project:**
- `CompletableFuture.supplyAsync(...)` in `TaskService.findFullById()` uses a different thread → **MDC context is lost** in async branches. The `DelegatingSecurityContextExecutor` propagates security context but NOT MDC. Need `MDCCopyExecutor` or similar wrapper.
- `requestId` is generated fresh per request — it's not the same as the distributed trace ID (Zipkin/Sleuth). Logs now have two different IDs per request. Should use the trace ID as the `requestId`.

---

## 9. N+1 Prevention (Batch Loading)

**What it solves:** 1000 tasks → 1000 separate queries to load related data.

| | |
|---|---|
| **+** | List endpoint stays O(1) queries regardless of result size |
| **+** | Pattern is explicit and easy to audit |
| **+** | No JPA magic (no `@EntityGraph`, no `JOIN FETCH`) — predictable SQL |
| **-** | `toResponseList()` must be maintained separately from `toResponse()` — easy to forget |
| **-** | If batch size is huge (10k tasks), one query loads 10k rows into memory |
| **-** | More boilerplate than Hibernate lazy loading |

**Potential issues in this project:**
- `taskIds` set passed to `findByTaskIdIn(ids)` — if `ids` has 50,000 elements, Postgres `IN (...)` clause hits limits. Need pagination or chunked batching for very large sets.
- `toResponseList()` pattern must be applied consistently — if a developer adds a new list endpoint using `toResponse()` in a stream, N+1 is silently reintroduced. No static analysis catches this. Consider an ArchUnit test.

---

## 10. Distributed Cache + Warmup

**What it solves:** reduces latency and user-service load for frequently accessed user data.

| | |
|---|---|
| **+** | Warmup prevents cold-start stampede on deployment |
| **+** | TTL-based expiry prevents stale data buildup |
| **+** | Redis survives service restarts (unlike in-memory cache) |
| **-** | Cache + DB = two sources of truth → stale reads possible within TTL window |
| **-** | Redis becomes a dependency — if Redis is down, service degrades |
| **-** | `@CacheEvict(allEntries=true)` on write flushes the entire cache — correct but expensive |

**Potential issues in this project:**
- Warmup calls `userClient.getUsers(0, WARMUP_PAGE_SIZE)` — if `WARMUP_PAGE_SIZE` < total users, cache is partially warmed. First requests for uncached users still hit user-service.
- `allEntries=true` eviction: if task-service has 100 cached users and one user updates their profile, **all 100 are evicted**. Next 100 requests miss. Better: evict by key, but then list-cache vs individual-cache consistency is tricky.
- No circuit breaker on Redis — if Redis times out, the exception propagates to the caller instead of falling back to a direct DB/service call.

---

## Priority Fix Summary

| Pattern | Biggest Risk | Priority Fix |
|---|---|---|
| Outbox | Duplicate publish under horizontal scale | Verify `SKIP LOCKED` in query |
| Choreography Saga | Silent partial failures | Add saga correlation ID + alerting |
| Ports & Adapters | Inconsistently applied | Apply to task-service or remove from user-service |
| Idempotent Consumer | `processed_events` table grows unbounded | Add TTL cleanup job |
| DLQ | Nobody watches it | Add Prometheus metric + alert |
| Pessimistic + Optimistic Lock | Both used simultaneously = redundant | Pick one per use case |
| AOP Logging | Param name-based masking is fragile | Use annotation-based masking |
| MDC Filter | MDC lost in `@Async` threads | Wrap async executor with MDC propagator |
| N+1 Prevention | Easy to regress silently | Add ArchUnit architecture test |
| Distributed Cache | Warmup only covers first page of users | Implement full warmup pagination |

### Three highest-risk issues for production:
1. **Outbox publisher races under horizontal scale** — could publish events multiple times
2. **MDC lost in async threads** — makes distributed debugging near-impossible
3. **No DLQ monitoring** — failures are completely silent

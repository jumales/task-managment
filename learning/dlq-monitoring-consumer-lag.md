# DLQ Monitoring with Consumer Lag (PR #176)

## TL;DR

The original `/api/v1/dlq/status` endpoint reported **end offsets** of each dead-letter
topic. That number never decreases, so a system that processed 10 old DLT messages and
has 0 new ones still reported `count: 10`. This PR replaces end offset with
**consumer lag** (`end_offset − committed_offset`) — a signal that drops to zero
once DLT messages are processed or replayed, and composes cleanly with health checks
and Prometheus alerting.

In the process, three pre-existing bugs in the audit-service test path were uncovered
(hidden behind a 9-day CI outage) and fixed.

---

## Why this matters

### What a DLT actually is

In this project, `KafkaDlqConfig` (in `common/`) wraps every `@KafkaListener` with
bounded retry + dead-letter forwarding:

- 3 attempts with exponential backoff (1s, 2s)
- On exhaustion, the record is forwarded to `<original-topic>.DLT`
- The original consumer's offset is committed so the partition keeps moving

Three DLTs exist:

| Source topic | DLT | Produced when |
|---|---|---|
| `task-changed` | `task-changed.DLT` | `audit-group` consumer throws for 3 attempts |
| `task-events` | `task-events.DLT` | `audit-lifecycle-group` or `search-group` consumer fails |
| `user-events` | `user-events.DLT` | `search-group` consumer fails |

A non-zero backlog on any DLT is a **real production incident**: those are events the
normal consumer failed to process after the entire retry budget. They represent data
the read model / projection / audit log has silently missed.

### The monitoring gap

Before this PR, the only way to know the DLT had messages was to manually hit
`GET /api/v1/dlq/status`. Three problems:

1. **Pull-based** — someone must remember to check. There was no alert, no health
   indicator, no Prometheus metric. Failures were silent until a user reported
   missing data.
2. **Wrong metric** — the endpoint returned the DLT's *end offset* (the total number
   of messages ever written). End offsets are monotonically increasing: the signal
   never drops back to 0 even after the DLT is drained, so it can't distinguish
   "active incident" from "historical failure, already resolved".
3. **Not actionable** — a number without a threshold, an owner, or an alert doesn't
   drive any behaviour.

### Why end offset is the wrong metric

```
time=T1: 10 messages dead-lettered → endOffset=10, consumerLag=10  ← real incident
time=T2: ops team replays them     → endOffset=10, consumerLag=0   ← resolved
time=T3: 3 new failures            → endOffset=13, consumerLag=3   ← new incident
```

At T2, end offset still says 10 → **false alarm** for any dashboard reading this number.
At T3, end offset says 13, which looks "worse than T1" but is actually a smaller incident
than T1. End offset is cumulative; consumer lag is *current*.

**Consumer lag** is the standard Kafka health signal:

```
lag = end_offset − committed_offset_for_consumer_group
```

It answers "how many unprocessed messages are sitting on this topic for this group?"
which is exactly the question a DLT monitor should answer.

---

## Solution: three monitoring surfaces, one lag source

### Architecture

```
       ┌─────────────────────┐
       │  DltLagService      │  ← single source of lag, uses AdminClient
       │  end − committed    │
       └──────────┬──────────┘
                  │
        ┌─────────┼─────────┐
        │         │         │
        ▼         ▼         ▼
   DlqController  DltHealth  DltMetrics
   (REST)         Indicator  Publisher
                  (actuator)  (Prometheus)
```

One place computes the lag; three consumers expose it in different ways:

| Surface | Path | When to use |
|---|---|---|
| REST | `GET /api/v1/dlq/status` | Ops manually inspects; returns `dltConsumerLag` map |
| Health | `GET /actuator/health/dlt` | K8s readiness probe gate; alerts when any lag > 0 |
| Metric | `kafka.dlt.consumer.lag{topic=…}` | Prometheus scrape + Grafana dashboard + Alertmanager rule |

### Key design decisions

**1. Per-DLT consumer groups registered in `KafkaTopics`**

```java
public static final String TASK_CHANGED_DLT_GROUP = "dlt-task-changed-group";
public static final String TASK_EVENTS_DLT_GROUP  = "dlt-task-events-group";
public static final String USER_EVENTS_DLT_GROUP  = "dlt-user-events-group";
```

Each DLT gets its own dedicated consumer group. If no DLT consumer has committed
offsets yet, lag equals end offset — which is the correct behaviour (every
dead-lettered message is unprocessed until someone drains or replays it).

**2. `@Service` with `AdminClient` per call**

```java
try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
    DLT_GROUPS.forEach((topic, groupId) -> lags.put(topic, consumerLag(admin, topic, groupId)));
}
```

One AdminClient instance is created per `getAllLags()` call and closed immediately.
AdminClient holds a dedicated network thread pool, so leaving one open wastes
connections; creating one per invocation is cheap at 30s poll intervals.

**3. Tolerant of missing topic / missing group**

```java
// topic doesn't exist yet → no failures → lag 0
if (endOffsets.isEmpty()) return 0L;

// DLT consumer group never committed → treat as 0 committed → lag = end_offset
catch (Exception e) {
    return Collections.emptyMap();
}
```

Both conditions are expected in a healthy system. The service only returns
`UNKNOWN_LAG` (`-1`) on genuine AdminClient failures — something dashboards can
treat as a probe error rather than a data point.

**4. 30s refresh for Micrometer gauge**

```java
@Scheduled(fixedDelay = POLL_INTERVAL_MS) // 30s
public void refresh() { … }
```

AdminClient calls are synchronous and RPC-heavy. Sub-5s polling would hammer the
broker; 30s is fast enough for alerting (the alert rule has `for: 5m` anyway) and
cheap enough to run forever.

**5. Health indicator bean name is `dlt`**

```java
@Component("dlt")
public class DltHealthIndicator implements HealthIndicator { … }
```

Spring Boot maps `@Component("dlt")` + `HealthIndicator` to
`/actuator/health/dlt` and composes it into `/actuator/health` under
`components.dlt`. One readiness probe covers DB + Kafka + DLT lag.

---

## Potential issues and caveats

### 1. `management.endpoint.health.show-details: always` exposes internals

The audit-service override bumps `show-details` from `never` (global default) to
`always`. That makes the DLT lag per-topic visible to anyone who can hit
`/actuator/health`. For this project it's fine — actuator isn't routed through
the gateway — but on a public-facing deployment you'd want `when-authorized` +
an actuator security filter.

### 2. Choice of DLT consumer group ID is permanent

The group IDs written into `KafkaTopics` become the identity used by every
future DLT consumer (the replay endpoint in step 5 of the plan, any
draining tool, etc.). Changing them later resets the committed offset so all
historical DLT messages look unprocessed again. Pick the names deliberately.

### 3. AdminClient latency on hot brokers

`listConsumerGroupOffsets` is a group coordinator RPC. Under high broker load
(e.g. during a rebalance storm) it can take seconds. The health indicator
inherits that latency — if you wire it into a K8s readiness probe with a
5s timeout, a busy broker can flap the pod. Workarounds:

- Cache the lag in the `DltMetricsPublisher` and let the health indicator read
  from the cache instead of recomputing. (Not done in this PR — simple path
  first.)
- Run the probe at 10–15s timeout.
- Exclude DLT from the composite `/actuator/health` and surface it only on the
  child path.

### 4. Lag ≠ "something is wrong *now*"

A DLT message written 30 minutes ago is still counted. That's correct (it's
still unprocessed) but means a freshly restored dashboard will show the
historical backlog at full count. Ops needs to know that clearing the
backlog requires either replaying the messages or drainining the group with
a throwaway consumer — not just waiting.

### 5. Replay endpoint was deferred

The plan's Step 5 — `POST /api/v1/dlq/{topic}/replay` — was not implemented
in this PR. Without it, the only way to clear lag is a manual `kafka-console-consumer`
invocation, which is not ops-friendly. Worth adding before this is truly
production-grade.

---

## Bonus: three bugs the CI blackout was hiding

CI had been failing in 6–8 seconds for ~9 days (config error, not test failures)
so no one knew audit-service ITs were broken. Trying to run the new DLT ITs
locally surfaced three orthogonal bugs:

### Bug 1 — `KafkaDlqConfig` context startup failed

```
PlaceholderResolutionException: Could not resolve placeholder
'spring.kafka.bootstrap-servers' in value "${spring.kafka.bootstrap-servers}"
```

**Cause.** `KafkaDlqConfig` used `@Value("${spring.kafka.bootstrap-servers}")`.
Under Spring Boot 3.4 + Testcontainers 1.20, `@ServiceConnection` on a
`KafkaContainer` populates a `KafkaConnectionDetails` bean — but does **not**
register the value back as a raw `spring.kafka.bootstrap-servers` property.
Anything reading via `@Value("${…}")` sees nothing.

**Fix.** Inject `KafkaProperties` instead:

```java
public KafkaDlqConfig(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
}
// …
ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers()
```

Spring Boot's Kafka auto-config derives `KafkaProperties` from either source
(raw YAML or `ConnectionDetails`), so this works in production **and** tests.

**Lesson.** `@ServiceConnection` + `@Value` is a footgun. Always prefer
Spring Boot's typed config beans (`KafkaProperties`, `DataSourceProperties`,
etc.) when the bean might be wired from Testcontainers in tests.

### Bug 2 — audit-service missing `TestSecurityConfig`

```
No qualifying bean of type 'org.springframework.security.oauth2.jwt.JwtDecoder'
```

**Cause.** `common/SecurityConfig` registers
`http.oauth2ResourceServer().jwt()` which requires a `JwtDecoder` bean. Without
`issuer-uri`, the real decoder can't be auto-configured. PR #172 added
`TestSecurityConfig { @Bean JwtDecoder jwtDecoder() { return Mockito.mock(...); } }`
to task/reporting/user services — but missed audit-service.

**Fix.** Added `audit-service/.../TestSecurityConfig.java` mirroring the existing
pattern (mock JwtDecoder + permit-all filter chain + fixed test user UUID),
`@Import(TestSecurityConfig.class)` on each audit-service IT.

**Lesson.** When an IT of service X fails at `JwtDecoder` resolution, the fix
is a service-local `TestSecurityConfig` — not adding an `issuer-uri` or disabling
security globally.

### Bug 3 — `DltLagService` returned `-1` when DLT group didn't exist

`admin.listConsumerGroupOffsets(groupId)` throws if the group has never
committed offsets. The first version of `DltLagService` caught all exceptions
and returned `UNKNOWN_LAG`, which meant a freshly-deployed cluster (no DLT
consumer ever run) reported `-1` for every DLT — and the health indicator
never went `UP`.

**Fix.** Split committed-offset lookup into its own method that returns
`Collections.emptyMap()` on missing group. Lag then falls through to
`end − 0 = end_offset`, which is the correct behaviour pre-drain.

**Lesson.** Differentiate "data not yet produced" from "probe failed".
Return `0` (or an equivalent known-safe value) for the former and only
reserve sentinel values like `-1` for genuine system errors. Mixed signals
confuse alert rules.

---

## File map

```
common/.../config/KafkaTopics.java            MODIFY  DLT consumer group constants
common/.../config/KafkaDlqConfig.java         MODIFY  @Value → KafkaProperties injection

audit-service/.../service/DltLagService.java      NEW     shared lag computation
audit-service/.../health/DltHealthIndicator.java  NEW     /actuator/health/dlt
audit-service/.../metrics/DltMetricsPublisher.java NEW    Prometheus gauge @Scheduled
audit-service/.../controller/DlqController.java   MODIFY  endOffset → consumer lag

audit-service/src/test/.../TestSecurityConfig.java NEW    mock JwtDecoder + permit-all
audit-service/src/test/.../DltHealthIndicatorIT.java NEW  UP/DOWN + producing to DLT
audit-service/src/test/.../DltMetricsPublisherIT.java NEW gauge registered + refresh

config-repo/audit-service.yml                 MODIFY  show-details: always for DLT
postman/audit-service.postman_collection.json MODIFY  assert new dltConsumerLag field

docs/services/audit-service.mdx               MODIFY  DLQ monitoring section
docs/operations/monitoring.mdx                MODIFY  /actuator/health/dlt row
CHANGES.md                                    MODIFY  PR #176 entry (also synced #171–#175)
```

## Verification

```bash
# 1. Produce a malformed record (deserialization failure → DLT on first attempt)
kafka-console-producer --bootstrap-server localhost:9092 --topic task-changed <<< '{broken json'

# 2. Check REST endpoint
curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/api/v1/dlq/status | jq
# → { "dltConsumerLag": { "task-changed.DLT": 1, "task-events.DLT": 0, … }, … }

# 3. Check composite health
curl http://localhost:8085/actuator/health | jq '.components.dlt'
# → { "status": "DOWN", "details": { "task-changed.DLT": 1, … } }

# 4. Scrape Prometheus
curl http://localhost:8085/actuator/prometheus | grep kafka_dlt
# → kafka_dlt_consumer_lag{topic="task-changed.DLT"} 1.0
```

# Kafka Architecture — Full Reference

This document describes every Kafka concept, implementation decision, and known problem
in the task-management microservices project. It is the entry point; each section links
to deeper learning files where they exist.

---

## Table of Contents

1. [Why Kafka — the core need](#1-why-kafka--the-core-need)
2. [Broker setup (KRaft, no ZooKeeper)](#2-broker-setup-kraft-no-zookeeper)
3. [Topics and retention](#3-topics-and-retention)
4. [Events — the message contracts](#4-events--the-message-contracts)
5. [Producer: Transactional Outbox pattern](#5-producer-transactional-outbox-pattern)
6. [Consumer: shared factory wiring](#6-consumer-shared-factory-wiring)
7. [Error handling and DLQ](#7-error-handling-and-dlq)
8. [Consumer idempotency](#8-consumer-idempotency)
9. [MDC / distributed tracing](#9-mdc--distributed-tracing)
10. [DLQ monitoring](#10-dlq-monitoring)
11. [Concurrency and partition assignment](#11-concurrency-and-partition-assignment)
12. [Serialization strategy](#12-serialization-strategy)
13. [Consumer map — who listens to what](#13-consumer-map--who-listens-to-what)
14. [Testing strategy](#14-testing-strategy)
15. [Known problems and resolutions](#15-known-problems-and-resolutions)
16. [Not yet implemented](#16-not-yet-implemented)

---

## 1. Why Kafka — the core need

The project is a set of microservices that must stay in sync without coupling to each
other's databases. When a task changes state in `task-service`, five other services
need to react:

| Service | What it needs to know |
|---|---|
| `audit-service` | Every field change, comment, phase transition, work log |
| `notification-service` | Status changes, new comments (email + WebSocket) |
| `search-service` | Full task and user snapshots for Elasticsearch indexing |
| `reporting-service` | Work logs and task metadata for reporting projections |
| `file-service` | Which file IDs to soft-delete when a task is archived |

A synchronous REST call from task-service to each of these on every write would create
tight coupling, slow writes, and cascading failures. Kafka decouples producers from
consumers: task-service writes once, all consumers read independently, and a consumer
going offline doesn't block the writer.

---

## 2. Broker setup (KRaft, no ZooKeeper)

```yaml
# docker-compose.yml
kafka:
  image: confluentinc/cp-kafka:7.6.0
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller   # KRaft: single node is both roles
    CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qw"
    KAFKA_LISTENERS: |
      PLAINTEXT://kafka:29092,           # inter-container
      PLAINTEXT_HOST://0.0.0.0:9092,    # host-machine access (IDE-launched services)
      CONTROLLER://kafka:9093
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
  volumes:
    - kafka-data:/var/lib/kafka/data     # persisted across restarts
```

KRaft mode removes ZooKeeper as a dependency. The broker acts as its own controller.
A single broker is acceptable for dev; production would require at minimum 3 nodes for
quorum and `replication.factor=3` for topic durability.

`KAFKA_AUTO_CREATE_TOPICS_ENABLE: true` means topics are created on first message.
In production this should be `false` — explicit topic creation gives control over
partition count and replication factor.

---

## 3. Topics and retention

All topic name constants live in one class:

```
common/src/main/java/com/demo/common/config/KafkaTopics.java
```

| Constant | Topic name | Purpose | Retention |
|---|---|---|---|
| `TASK_CHANGED` | `task-changed` | All field-level changes inside tasks | 7 days |
| `TASK_EVENTS` | `task-events` | Task lifecycle (create / update / delete / archive) | 7 days |
| `USER_EVENTS` | `user-events` | User lifecycle | default |
| `TASK_CHANGED_DLT` | `task-changed.DLT` | Failed `task-changed` messages | — |
| `TASK_EVENTS_DLT` | `task-events.DLT` | Failed `task-events` messages | — |
| `USER_EVENTS_DLT` | `user-events.DLT` | Failed `user-events` messages | — |

Retention is configured in `config-repo/application.yml`:

```yaml
ttl:
  kafka:
    task-events-retention-hours: 168    # 7 days
    task-changed-retention-hours: 168   # 7 days
```

**Why one constant class?**  
String literals scattered across producers and consumers are an error-prone mirror —
a typo creates a new topic silently. One class is the single source of truth; any
rename is a compile-time break.

---

## 4. Events — the message contracts

All event classes live in `common` because they are shared between a producer and one
or more consumers in different modules.

### 4.1 `TaskChangedEvent` (→ `task-changed`)

A single unified event for **every fine-grained change** inside a task. The
`changeType` field (enum with 15 values) is the discriminator:

```
TASK_CREATED, TASK_UPDATED, STATUS_CHANGED, COMMENT_ADDED,
PHASE_CHANGED, PLANNED_WORK_CREATED,
BOOKED_WORK_CREATED, BOOKED_WORK_UPDATED, BOOKED_WORK_DELETED,
ATTACHMENT_ADDED, ATTACHMENT_DELETED,
PARTICIPANT_ADDED, PARTICIPANT_REMOVED,
TIMELINE_CHANGED, TASK_CODE_ASSIGNED
```

Every event carries an `eventId` (UUID) used for idempotent deduplication on the
consumer side. Factory methods on the class (e.g. `TaskChangedEvent.statusChanged(...)`)
ensure every required field is populated at the construction site.

**Design choice:** one topic + discriminator vs. one topic per change type.
A single topic keeps consumer group offset management simple and allows consumers
to filter in application code rather than subscribing to many topics. The trade-off
is that consumers receive events they don't care about and must short-circuit.

### 4.2 `TaskEvent` (→ `task-events`)

Lifecycle snapshots consumed by services that maintain a full copy of task data
(search index, reporting projection):

```
CREATED / UPDATED — full snapshot: code, title, description, status,
                     project, phase, assignee, timeline
DELETED           — taskId only
ARCHIVED          — taskId, archiveMonth (YYYYMM), archivedFileIds[]
```

### 4.3 `UserEvent` (→ `user-events`)

User lifecycle snapshots:

```
CREATED / UPDATED — userId, name, email, username, active flag
DELETED           — userId only
```

---

## 5. Producer: Transactional Outbox pattern

**Source:** `task-service/src/main/java/com/demo/task/outbox/`

### 5.1 The problem it solves

Dual-write problem: if a service writes to its DB and then calls
`kafkaTemplate.send(...)`, there is a window where the DB write commits but the Kafka
send fails (or vice versa). Data and events diverge.

### 5.2 The outbox table

```sql
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR   NOT NULL,  -- "TASK"
    aggregate_id   UUID      NOT NULL,  -- used as Kafka partition key
    event_type     VARCHAR   NOT NULL,  -- TASK_CHANGED / TASK_CREATED / ...
    topic          VARCHAR   NOT NULL,
    payload        TEXT      NOT NULL,  -- JSON-serialized event
    published      BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL
);
```

**Flow:**

```
Business logic (TaskService.update)
    │
    ├── UPDATE tasks SET ... WHERE id = ?          ┐
    └── INSERT INTO outbox_events (payload, ...)   ┘ one DB transaction
                    │
                    │  (transaction commits)
                    ▼
         OutboxPublisher (every 1 second)
              │
              ├── SELECT ... FOR UPDATE SKIP LOCKED  ← up to 500 rows
              ├── kafkaTemplate.send(topic, key, event)  for each
              ├── wait futures.get(10s)
              └── UPDATE outbox_events SET published = true
```

Both DB writes happen in the same transaction. If the transaction rolls back, no event
is produced. The outbox table is the source of truth; Kafka delivery is a background
best-effort operation that eventually succeeds.

### 5.3 `SKIP LOCKED`

```java
@Query("SELECT e FROM OutboxEvent e WHERE e.published = false ORDER BY e.createdAt " +
       "FOR UPDATE SKIP LOCKED")
List<OutboxEvent> findUnpublishedForUpdate(Pageable pageable);
```

When multiple task-service instances run, each polls the same outbox table. `FOR UPDATE`
locks the selected rows; `SKIP LOCKED` makes other instances skip already-locked rows
rather than waiting. This prevents double-publishing across instances without a
distributed lock.

**What `SKIP LOCKED` does NOT prevent:** if an instance sends the event and crashes
before committing `published = true`, the next poll re-sends. This is
**at-least-once delivery** — duplicates are possible. Consumer-side idempotency
handles this. See section 8 and `learning/outbox-consumer-idempotency.md`.

### 5.4 `OutboxWriter`

Helper component that serializes and writes events. Task-service service layer calls
this instead of constructing `OutboxEvent` directly, keeping serialization concerns
in one place.

```java
// task-service: writing a status change event
outboxWriter.write(TaskChangedEvent.statusChanged(taskId, projectId, oldStatus, newStatus));

// task-service: writing a lifecycle event
outboxWriter.writeTaskEvent(taskId, OutboxEventType.TASK_UPDATED, taskEvent);
```

### 5.5 Partition key

`aggregate_id` (task UUID) is used as the Kafka message key. This means all events for
the same task land on the same partition, preserving event order per task.

### 5.6 Outbox cleanup

`OutboxCleanupService` runs nightly (4 AM cron):

```java
outboxRepository.deletePublishedOlderThan(Instant.now().minus(30, DAYS));
```

Without cleanup, the table grows unbounded. 30 days is a conservative retention
aligned with the 7-day Kafka retention — a message published 30 days ago cannot be
a duplicate of anything Kafka still holds.

### 5.7 User-service direct producer (no outbox)

`user-service` uses `KafkaUserEventPublisher` — a direct `KafkaTemplate.send()` without
an outbox. This is intentional: user events are best-effort in the current dev setup.
The trade-off is at-most-once semantics (a crash between send and ACK loses the event)
vs. the complexity of adding an outbox to user-service. Acceptable for dev; should be
revisited before production.

---

## 6. Consumer: shared factory wiring

**Source:** `common/src/main/java/com/demo/common/config/KafkaDlqConfig.java`  
Each service: `<service>/src/main/java/com/demo/<service>/config/KafkaConsumerConfig.java`

Every consuming service (`audit-service`, `notification-service`, `search-service`,
`reporting-service`, `file-service`) has an identical `KafkaConsumerConfig`:

```java
@Configuration
@Import(KafkaDlqConfig.class)    // pulls in DLQ beans from common
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,  // auto-configured by Boot
            DefaultErrorHandler kafkaErrorHandler,            // from KafkaDlqConfig
            RecordInterceptor<Object, Object> mdcRecordInterceptor) { // from KafkaDlqConfig
        var factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setRecordInterceptor(mdcRecordInterceptor);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        return factory;
    }
}
```

### Why `@Import(KafkaDlqConfig.class)` and not component scan?

`KafkaDlqConfig` is in `common` and has **no `@Configuration` annotation**. This is
intentional. Every service scans `com.demo.**`, which includes common. If
`KafkaDlqConfig` were `@Configuration`, it would activate in producer-only services
(like `task-service`) — registering a DLT `KafkaTemplate` factory and interfering with
Boot's auto-configuration. By making it opt-in via `@Import`, only services that
explicitly declare `@Import(KafkaDlqConfig.class)` get the DLQ beans.

### Why `AckMode.RECORD`?

| Mode | Commits when | Risk |
|---|---|---|
| `BATCH` (Boot default) | After the entire poll batch | One failure can reprocess the whole batch |
| `RECORD` | After each record | Minimal reprocessing scope on failure |
| `MANUAL_IMMEDIATE` | Listener calls `ack.acknowledge()` | Developer must not forget |

`RECORD` is safe here because `DefaultErrorHandler` controls offset commits on error:
after retry exhaustion it forwards to DLT and then commits the offset. Without a
`DefaultErrorHandler`, a persistent exception under `RECORD` mode would not commit
and the record would replay indefinitely.

### Why does this bean override Boot's auto-configured factory?

Spring Boot auto-configures a `ConcurrentKafkaListenerContainerFactory` bean via
`@ConditionalOnMissingBean`. Declaring a bean with the same name
(`kafkaListenerContainerFactory`) shadows Boot's default entirely. The `ConsumerFactory`
is still auto-configured from `spring.kafka.consumer.*` YAML properties.

---

## 7. Error handling and DLQ

**Source:** `common/src/main/java/com/demo/common/config/KafkaDlqConfig.java`

### Retry pipeline

```
Kafka poll → listener method
                │
                ├── success → RECORD ack → next record
                │
                └── throws
                       │
                  DefaultErrorHandler
                       │
                  attempt 2 (after ~1 s)
                       │
                  attempt 3 (after ~2 s)
                       │
                  exhausted
                       │
              DeadLetterPublishingRecoverer
                  publish to <topic>.DLT
                  RECORD ack → consumer moves on
```

`ExponentialBackOff(1_000L, 2.0)` with `maxAttempts = 2` = 2 retries = 3 total attempts.

### Non-retryable exceptions

`DeserializationException` is added to `handler.addNotRetryableExceptions(...)`. A
malformed JSON byte sequence will fail deserialization on every attempt — retrying it
is pointless and delays other records. It goes straight to DLT on first failure.

### The inline `KafkaTemplate` trick

`DeadLetterPublishingRecoverer` needs its own `KafkaTemplate` to write to the DLT.
Creating this template as a Spring bean would satisfy Spring Boot's
`@ConditionalOnMissingBean(KafkaOperations.class)` guard and prevent the service's own
auto-configured `KafkaTemplate` from being created (breaking all producers). The template
is created with `new` inside the `@Bean` method — it operates normally but is invisible
to Boot's auto-configuration.

### DLT serialization

The DLT template uses `JsonSerializer` (not `ByteArraySerializer`). By the time a
processing error reaches the recoverer, the record value is already a deserialized Java
object — `ByteArraySerializer` cannot serialize that. `JsonSerializer` handles both
deserialized objects (processing exceptions) and raw bytes extracted from
`DeserializationException.getData()` (deserialization exceptions).
Type-info headers are disabled so DLT consumers aren't constrained to a specific class.

---

## 8. Consumer idempotency

**Full explanation:** `learning/outbox-consumer-idempotency.md`

### Two tiers

| Consumer | Side effect | Strategy |
|---|---|---|
| `audit/TaskEventConsumer` | INSERT into audit tables | Explicit dedup |
| `audit/TaskLifecycleConsumer` | Bulk archive moves | Explicit dedup |
| `notification/TaskEventNotificationConsumer` | Email + WebSocket push | Explicit dedup |
| `reporting/TaskEventProjectionConsumer` | Upsert + WebSocket push | Explicit dedup |
| `reporting/TaskChangedProjectionConsumer` | Upsert + WebSocket push | Explicit dedup |
| `search/TaskEventConsumer` | ES upsert by ID | Natural (idempotent by design) |
| `search/UserEventConsumer` | ES upsert by ID | Natural (idempotent by design) |
| `file/TaskArchivedConsumer` | `softDeleteById` | Natural (no-op once deleted_at set) |

### The dedup table

```sql
CREATE TABLE processed_kafka_events (
    id             UUID PRIMARY KEY,
    event_id       UUID NOT NULL,
    consumer_group VARCHAR(255) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uidx_processed_kafka_events_event_consumer
    ON processed_kafka_events (event_id, consumer_group);
```

`consumer_group` is part of the composite unique key because `task-service` publishes
one event with one `eventId`, but `audit-service` and `notification-service` both
consume it independently — they must not block each other.

### `REQUIRES_NEW` propagation

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean markProcessed(UUID eventId, String consumerGroup) {
    try {
        repository.save(...);
        return true;
    } catch (DataIntegrityViolationException e) {
        return false;
    }
}
```

The dedup row is committed in its own transaction **before** the consumer does real work.
If the consumer's transaction later rolls back, the dedup row survives — the event is
treated as processed and subsequent re-deliveries are discarded. If `REQUIRED` were used
instead, a consumer rollback would also roll back the dedup row, causing infinite
reprocessing on every retry.

---

## 9. MDC / distributed tracing

**Source:** `KafkaDlqConfig.mdcRecordInterceptor()` bean

The `RecordInterceptor` runs around every listener invocation:

```
intercept()       → extracts correlationId header (or generates UUID fallback)
                    puts into MDC: requestId, kafkaTopic, kafkaPartition
[listener runs]
afterRecord()     → MDC.clear()   ← prevents context leaking to next record's thread
```

Producers (OutboxPublisher) stamp outgoing messages with the `correlationId` header:

```java
headers.add("correlationId", currentRequestId.getBytes(StandardCharsets.UTF_8));
```

Result: a log entry in `task-service` that triggered an outbox event and the
corresponding log entry in `audit-service` when it processes the event share the same
`requestId`. In JSON-structured logs (Logstash/Kibana) this allows filtering an entire
distributed operation across services without Zipkin or OTEL.

---

## 10. DLQ monitoring

**Full explanation:** `learning/dlq-monitoring-consumer-lag.md`

### Three monitoring surfaces

```
DltLagService  (AdminClient, computes lag = end_offset − committed_offset)
      │
      ├── DlqController         GET /api/v1/dlq/status     → JSON for ops inspection
      ├── DltHealthIndicator    /actuator/health/dlt        → K8s readiness / alert
      └── DltMetricsPublisher   kafka.dlt.consumer.lag{topic=…}  → Prometheus / Grafana
```

### Why consumer lag, not end offset?

End offset is monotonically increasing — it never drops after DLT messages are replayed
or drained. Consumer lag (`end − committed`) drops to zero once the DLT is drained,
making it a true "current incident" signal.

### AdminClient per call

One `AdminClient` is created and closed per `getAllLags()` invocation. AdminClient
maintains a dedicated thread pool; leaving one open permanently wastes connections.
At a 30-second poll interval, the creation overhead is negligible.

### DLT consumer groups

Each DLT topic has a dedicated consumer group ID registered in `KafkaTopics`:

```java
TASK_CHANGED_DLT_GROUP  = "dlt-task-changed-group"
TASK_EVENTS_DLT_GROUP   = "dlt-task-events-group"
USER_EVENTS_DLT_GROUP   = "dlt-user-events-group"
```

These group IDs are **permanent**. Changing them resets committed offsets, making all
historical DLT messages appear unprocessed again.

---

## 11. Concurrency and partition assignment

`@KafkaListener` supports a `concurrency` attribute that controls how many consumer
threads (and thus how many partitions can be consumed in parallel) the listener container
creates:

| Consumer | Topic | Group | Concurrency |
|---|---|---|---|
| `audit/TaskEventConsumer` | `task-changed` | `audit-group` | 12 |
| `audit/TaskLifecycleConsumer` | `task-events` | `audit-lifecycle-group` | 3 |
| `notification/TaskEventNotificationConsumer` | `task-changed` | `notification-group` | 3 |
| `search/TaskEventConsumer` | `task-events` | `search-group` | 3 |
| `search/UserEventConsumer` | `user-events` | `search-group` | 3 |
| `reporting/TaskChangedProjectionConsumer` | `task-changed` | `reporting-group` | 3 |
| `reporting/TaskEventProjectionConsumer` | `task-events` | `reporting-group` | 3 |
| `file/TaskArchivedConsumer` | `task-events` | `file-archive-group` | — |

`audit-service` has concurrency 12 because it processes every fine-grained change event
and is the highest-throughput consumer. Concurrency beyond the partition count is wasted
— idle threads hold no partition assignment.

---

## 12. Serialization strategy

### Producer side (task-service outbox)

`OutboxWriter` serializes the event to JSON string using Jackson `ObjectMapper`, then
stores it as `TEXT` in the outbox table. `OutboxPublisher` sends the JSON string as
the Kafka message value using `StringSerializer`.

### Consumer side (all services)

`spring.kafka.consumer.value-deserializer: JsonDeserializer` with
`spring.kafka.consumer.properties.spring.json.trusted.packages: "com.demo.*"`.

The `JsonDeserializer` reads the type from the `__TypeId__` header (or maps the
JSON to a target type specified in `@KafkaListener`). All event classes are in
`common` so the package trust list is the same across all services.

### DLT serialization

The inline DLT `KafkaTemplate` uses `JsonSerializer` (not `ByteArraySerializer`)
because by the time a processing exception reaches the recoverer, the record value
is already a deserialized Java object. `ByteArraySerializer` cannot serialize a POJO.
`JsonSerializer.ADD_TYPE_INFO_HEADERS = false` prevents type headers from restricting
which class a DLT consumer must use for deserialization.

---

## 13. Consumer map — who listens to what

```
task-changed ──────────────────────────────────────────────────────────────────┐
                                                                               │
    ┌─ audit-service     (audit-group, concurrency=12)                         │
    │  Routes by changeType:                                                   │
    │  STATUS_CHANGED → StatusAuditRecord                                      │
    │  COMMENT_ADDED  → CommentAuditRecord                                     │
    │  PHASE_CHANGED  → PhaseAuditRecord                                       │
    │  PLANNED_WORK_* → PlannedWorkAuditRecord                                 │
    │  BOOKED_WORK_*  → BookedWorkAuditRecord                                  │
    │                                                                          │
    ├─ notification-service (notification-group, concurrency=3)                │
    │  → NotificationService.notify(event)  [async]                           │
    │  → TaskPushService.push(taskId, changeType)  [WebSocket]                │
    │                                                                          │
    └─ reporting-service (reporting-group, concurrency=3)                     │
       Routes by changeType:                                                   │
       PLANNED_WORK_CREATED      → upsertPlannedWork                          │
       BOOKED_WORK_CREATED/UPD   → upsertBookedWork + WebSocket push         │
       BOOKED_WORK_DELETED       → softDeleteBookedWork                        │
                                                                               │
task-events ───────────────────────────────────────────────────────────────────┤
                                                                               │
    ┌─ audit-service  (audit-lifecycle-group, concurrency=3)                   │
    │  Only ARCHIVED events → AuditArchiveService.archiveTask                  │
    │                                                                          │
    ├─ search-service  (search-group, concurrency=3)                           │
    │  CREATED / UPDATED → index(event) in Elasticsearch                       │
    │  DELETED / ARCHIVED → delete(event) from Elasticsearch                   │
    │                                                                          │
    ├─ reporting-service (reporting-group, concurrency=3)                      │
    │  CREATED / UPDATED → upsert task projection + WebSocket push            │
    │  DELETED           → softDelete projection                               │
    │  ARCHIVED          → hard-delete all projections (booked/planned/task)   │
    │                                                                          │
    └─ file-service  (file-archive-group)                                      │
       Only ARCHIVED events → softDeleteById for each archivedFileId           │
                                                                               │
user-events ───────────────────────────────────────────────────────────────────┘
    └─ search-service  (search-group, concurrency=3)
       CREATED / UPDATED → index user in Elasticsearch
       DELETED           → delete user from Elasticsearch
```

---

## 14. Testing strategy

### 14.1 Testcontainers setup

Kafka ITs spin up real brokers via Testcontainers:

```java
@Container
static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

@DynamicPropertySource
static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
}
```

`@ServiceConnection` can also be used — it auto-registers `KafkaConnectionDetails` and
populates `KafkaProperties` without a `@DynamicPropertySource`. Both patterns are used
across the project.

### 14.2 `@DirtiesContext` on Kafka IT classes

**Required** for any `@SpringBootTest` that:
- Spins up a real Kafka broker via Testcontainers
- Has any `@Scheduled` component that publishes to Kafka (e.g. `OutboxPublisher`)

Without it: Testcontainers stops the broker, but `SpringExtension` keeps the
`ApplicationContext` alive for context reuse. `OutboxPublisher` fires every 5 seconds,
hitting a dead broker. The Kafka NetworkClient thread (non-daemon) prevents JVM exit;
Surefire force-kills after 30 seconds.

See `learning/kafka-producer-jvm-shutdown-hang.md` for full root cause analysis.

### 14.3 Await for async consumption

Consumer ITs use `Awaitility` since `@KafkaListener` processing is asynchronous:

```java
await().atMost(15, SECONDS).untilAsserted(() -> {
    assertThat(statusAuditRepository.findAll()).hasSize(1);
});
```

15 seconds covers slow CI environments and Testcontainers startup overhead.

### 14.4 Idempotency tests

Each explicit-dedup consumer has an IT that sends the same event twice and asserts
the side effect ran exactly once:

```java
publish(event);
publish(event);  // same eventId

await().atMost(20, SECONDS).untilAsserted(() -> {
    assertThat(bookedRepository.findAll()).hasSize(1);              // work inserted once
    assertThat(processedEventRepository.count()).isEqualTo(1);     // dedup fired
});
```

### 14.5 DLT integration tests

`DltHealthIndicatorIT` in `audit-service`:
- Sends a malformed record → verify DLT receives it and health goes DOWN
- Verifies health UP when all DLTs are empty

---

## 15. Known problems and resolutions

### 15.1 JVM shutdown hang in Kafka ITs

**Symptom:** test suite hangs 30–60 seconds after completion; Surefire force-kills JVM.  
**Cause:** Non-daemon Kafka NetworkClient threads + `@Scheduled` OutboxPublisher + Spring
context caching = threads alive after broker stopped.  
**Fix:** `@DirtiesContext` on all Kafka IT classes.  
**See:** `learning/kafka-producer-jvm-shutdown-hang.md`

### 15.2 `@Value` vs `@ServiceConnection` in Testcontainers

**Symptom:** `PlaceholderResolutionException: Could not resolve placeholder
'spring.kafka.bootstrap-servers'` in IT startup.  
**Cause:** `@ServiceConnection` populates `KafkaConnectionDetails` — a typed bean — but
does NOT register the raw `spring.kafka.bootstrap-servers` property. `@Value("${...}")`
reads the raw property registry and finds nothing.  
**Fix:** Inject `KafkaProperties` (Boot's typed config object) instead of `@Value`.  
`KafkaProperties.getBootstrapServers()` reads from either source (raw YAML or
`ConnectionDetails`).

### 15.3 DLT consumer group never committed → health stuck DOWN

**Symptom:** freshly deployed audit-service always reports DLT health DOWN even with
empty DLT topics.  
**Cause:** `admin.listConsumerGroupOffsets(groupId)` throws when the group has never
committed. Early implementation caught the exception and returned `UNKNOWN_LAG (-1)`.
The health indicator mapped `-1` to DOWN.  
**Fix:** Return `Collections.emptyMap()` for a missing group — lag then computes as
`end - 0 = end_offset`. For empty topics, end offset is 0, so lag is 0 → UP.  
**See:** `learning/dlq-monitoring-consumer-lag.md`

### 15.4 Missing `TestSecurityConfig` in audit-service ITs

**Symptom:** `NoSuchBeanDefinitionException: JwtDecoder` on audit-service IT startup.  
**Cause:** `common/SecurityConfig` registers `oauth2ResourceServer().jwt()` which
requires a real `JwtDecoder`. Test contexts have no `issuer-uri` to auto-configure one.
The fix was applied to task/reporting/user services but missed audit-service.  
**Fix:** Create `audit-service/src/test/.../TestSecurityConfig.java` with a mock
`JwtDecoder` bean and `@Import` it in each IT class.

---

## 16. Not yet implemented

| Item | Why it matters | Current workaround |
|---|---|---|
| DLT replay endpoint (`POST /dlq/{topic}/replay`) | Without it, clearing DLT backlog requires manual `kafka-console-consumer` | Manual ops |
| `processed_kafka_events` cleanup scheduler | Table grows unbounded across all consuming services | Manual truncation |
| Kafka transactional producer (Option B idempotency) | Eliminates duplicate delivery at source; safer than consumer-side dedup | Consumer dedup table |
| `KAFKA_AUTO_CREATE_TOPICS_ENABLE: false` + explicit topic setup | Production safety: accidental topic creation via typos | Dev-only: auto-create OK |
| Multiple Kafka brokers / replication factor > 1 | Single broker = no redundancy; replication requires ≥ 3 nodes for transactions | Dev-only single broker |
| `processed_kafka_events` retention in user-service | user-service has no outbox or dedup table; `UserEvent` delivery is at-most-once | Direct KafkaTemplate |

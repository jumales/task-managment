# Fix: DLQ Monitoring — Silent Failures in Dead-Letter Topics

## Context

`KafkaDlqConfig` (common) routes failed Kafka messages to `<topic>.DLT` after 3 attempts.
`DlqController.GET /api/v1/dlq/status` in `audit-service` returns the **end offset** of each DLT.

This is a manual, pull-based check — someone must call the endpoint to discover failures.
More critically, **end offset is the wrong metric**: it measures total messages ever written to
the DLT, not unprocessed ones. It never decreases, so a system that processed 10 old DLT
messages and has 0 new ones will still show `count: 10`.

## Current State

| Component | File | Limitation |
|---|---|---|
| `DlqController` | `audit-service/.../controller/DlqController.java:28` | Returns end offsets, not consumer lag |
| `KafkaDlqConfig` | `common/.../config/KafkaDlqConfig.java:35` | Routes to DLT but no metrics |
| DLT topics | `KafkaTopics.java` | `TASK_CHANGED_DLT`, `TASK_EVENTS_DLT`, `USER_EVENTS_DLT` defined |
| Alerting | — | None — failures are completely silent |
| Actuator health | — | Not reflected in `/actuator/health` |

## Problem Details

```
End offset grows forever:
  time=T1: 10 messages dead-lettered → endOffset=10, consumerLag=10  ← real problem
  time=T2: ops team processes them   → endOffset=10, consumerLag=0   ← resolved
  time=T3: 3 more failures           → endOffset=13, consumerLag=3   ← real problem

DlqController at T2 returns 10 (false alarm).
DlqController at T3 returns 13 (actual lag=3, but looks "worse" than T1).
```

Consumer lag = `end_offset − committed_offset_for_consumer_group` is the correct signal.

## Plan

### Step 1 — Fix `DlqController`: return consumer lag, not end offset

**File**: `audit-service/src/main/java/com/demo/audit/controller/DlqController.java`

Replace `endOffset()` helper with `consumerLag(AdminClient, String, String consumerGroup)`:

```java
private long consumerLag(AdminClient admin, String topic, String groupId) {
    try {
        // Get end offsets
        var descriptions = admin.describeTopics(List.of(topic)).allTopicNames().get();
        if (!descriptions.containsKey(topic)) return 0L; // topic doesn't exist = no failures

        Map<TopicPartition, OffsetSpec> query = new HashMap<>();
        descriptions.get(topic).partitions()
                    .forEach(p -> query.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                admin.listOffsets(query).all().get();

        // Get committed offsets for the DLT consumer group
        var committed = admin.listConsumerGroupOffsets(groupId)
                             .partitionsToOffsetAndMetadata().get();

        return endOffsets.entrySet().stream().mapToLong(e -> {
            long end = e.getValue().offset();
            long committed = Optional.ofNullable(committed.get(e.getKey()))
                                     .map(OffsetAndMetadata::offset)
                                     .orElse(0L);
            return Math.max(0, end - committed);
        }).sum();
    } catch (Exception e) {
        return -1L;
    }
}
```

Rename response field `dltMessageCounts` → `dltConsumerLag`.

### Step 2 — Add `DltHealthIndicator` in `audit-service`

**File**: `audit-service/src/main/java/com/demo/audit/health/DltHealthIndicator.java`

```java
/**
 * Reports DOWN when any DLT topic has unconsumed messages (consumer lag > 0).
 * Exposes via /actuator/health/dlt — can gate Kubernetes readiness probe.
 */
@Component
public class DltHealthIndicator implements HealthIndicator {

    private final DltLagService dltLagService; // extracted from DlqController

    @Override
    public Health health() {
        Map<String, Long> lags = dltLagService.getAllLags();
        boolean anyUnconsumed = lags.values().stream().anyMatch(lag -> lag > 0);
        if (anyUnconsumed) {
            return Health.down()
                         .withDetails(lags)
                         .withDetail("message", "Dead-letter topics have unconsumed messages")
                         .build();
        }
        return Health.up().withDetails(lags).build();
    }
}
```

Expose via `application.yml`:
```yaml
management:
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health, prometheus, info
```

### Step 3 — Add Micrometer gauge for DLT consumer lag

**File**: `audit-service/src/main/java/com/demo/audit/metrics/DltMetricsPublisher.java`

```java
/**
 * Publishes DLT consumer lag as a Micrometer gauge on a fixed schedule.
 * Prometheus scrapes this via /actuator/prometheus.
 * Alert rule: kafka_dlt_consumer_lag > 0 for 5 minutes → PagerDuty/Slack
 */
@Component
public class DltMetricsPublisher {

    private final MeterRegistry meterRegistry;
    private final DltLagService dltLagService;
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerGauges() {
        for (String topic : List.of(KafkaTopics.TASK_CHANGED_DLT,
                                     KafkaTopics.TASK_EVENTS_DLT,
                                     KafkaTopics.USER_EVENTS_DLT)) {
            AtomicLong value = new AtomicLong(0);
            gaugeValues.put(topic, value);
            Gauge.builder("kafka.dlt.consumer.lag", value, AtomicLong::get)
                 .tag("topic", topic)
                 .description("Number of unprocessed messages in DLT topic")
                 .register(meterRegistry);
        }
    }

    @Scheduled(fixedDelay = 30_000) // poll every 30s
    public void refresh() {
        dltLagService.getAllLags().forEach((topic, lag) -> {
            if (gaugeValues.containsKey(topic)) gaugeValues.get(topic).set(lag);
        });
    }
}
```

### Step 4 — Extract `DltLagService` shared by controller, health, and metrics

**File**: `audit-service/src/main/java/com/demo/audit/service/DltLagService.java`

Extracts the `AdminClient` lag-computation logic into one place so `DlqController`,
`DltHealthIndicator`, and `DltMetricsPublisher` all share the same implementation.

### Step 5 (optional) — DLT replay endpoint

**File**: Add `POST /api/v1/dlq/{topic}/replay` to `DlqController`

- Admin-only (`@PreAuthorize("hasRole('ADMIN')")`)
- Reads records from the DLT topic using a dedicated `KafkaConsumer` (not a `@KafkaListener`)
- Re-publishes each record to the original topic (strip `.DLT` suffix)
- Commits offset after successful re-publish
- Returns count of replayed messages

## Files to Create / Modify

```
audit-service/.../controller/DlqController.java          MODIFY — fix end offset → consumer lag, add replay
audit-service/.../health/DltHealthIndicator.java         NEW
audit-service/.../metrics/DltMetricsPublisher.java       NEW
audit-service/.../service/DltLagService.java             NEW — extract shared AdminClient logic
audit-service/src/main/resources/application.yml         MODIFY — expose health/dlt actuator endpoint
audit-service/src/test/.../DltHealthIndicatorIT.java     NEW
audit-service/src/test/.../DltMetricsPublisherIT.java    NEW
```

## Verification

```bash
# 1. Produce a message that will fail all retries (e.g. malformed JSON → DeserializationException)
#    → Message lands in task-changed.DLT after first attempt

# 2. Check health:
curl http://localhost:8085/actuator/health | jq '.components.dlt'
# Expected: { "status": "DOWN", "details": { "task-changed.DLT": 1, ... } }

# 3. Check DLQ status (now returns consumer lag, not end offset):
curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/api/v1/dlq/status
# Expected: { "dltConsumerLag": { "task-changed.DLT": 1, ... } }

# 4. Scrape Prometheus metrics:
curl http://localhost:8085/actuator/prometheus | grep kafka_dlt
# Expected: kafka_dlt_consumer_lag{topic="task-changed.DLT"} 1.0

# 5. (Optional) Replay the dead-lettered message:
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8085/api/v1/dlq/task-changed.DLT/replay
# Expected: { "replayed": 1 }
# → Original consumer processes it, lag drops to 0
```

## Execution Notes

- `DltLagService` requires knowing the consumer group ID for each DLT topic — document these in
  `KafkaTopics.java` as constants alongside the DLT topic name constants
- If no consumer group has ever committed to a DLT (no DLT consumer exists), lag = end offset
  (every dead-lettered message is unprocessed — correct behavior)
- `@Scheduled(fixedDelay = 30_000)` is a reasonable polling interval; avoid sub-5s polling as
  `AdminClient` calls are synchronous and relatively expensive
- Prometheus alert rule (add to `monitoring/alert-rules.yml` if it exists):
  ```yaml
  - alert: DltMessagesPending
    expr: kafka_dlt_consumer_lag > 0
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Dead-letter topic {{ $labels.topic }} has {{ $value }} unprocessed messages"
  ```
- Recommended branch: `fix_dlq_monitoring`

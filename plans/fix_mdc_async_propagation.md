# Fix: MDC Lost in Async Threads

## Context

`MdcFilter` (common) populates the MDC with `requestId`, `method`, `path`, `userId` on every
HTTP request thread. These values are **thread-local** — they do not transfer automatically to:

- Threads spawned by `@Async` methods (Spring's default `SimpleAsyncTaskExecutor`)
- Kafka `@KafkaListener` consumer threads

Result: all logs from async operations are untraced, making it impossible to correlate an
async side effect (e.g. default phase creation, notification delivery) with the originating
HTTP request in Kibana/Grafana.

## Current State

| Component | File | MDC propagation |
|---|---|---|
| `MdcFilter` | `common/.../web/MdcFilter.java:33` | Sets MDC on HTTP threads only |
| `@Async createDefaultPhasesForProject()` | `task-service/.../service/TaskPhaseService.java:64` | No MDC — blank `requestId` in logs |
| `@Async notify()` | `notification-service/.../service/NotificationService.java:60` | No MDC — blank `requestId` in logs |
| `@KafkaListener` consumers (all services) | Various `*Consumer.java` files | No MDC — blank `requestId` in logs |
| `@EnableAsync` | `TaskServiceApplication`, `NotificationServiceApplication`, `UserServiceApplication` | Each service enables its own async — no shared executor config |

## Plan

### Step 1 — Create `MdcTaskDecorator` in `common`

**File**: `common/src/main/java/com/demo/common/web/MdcTaskDecorator.java`

```java
/**
 * Propagates the caller thread's MDC context map into the async thread.
 * Without this, @Async methods and Spring task executor threads start with an empty MDC,
 * making it impossible to correlate their log lines with the originating HTTP request.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) MDC.setContextMap(contextMap);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
```

### Step 2 — Register `ThreadPoolTaskExecutor` bean in `common`

**File**: `common/src/main/java/com/demo/common/config/AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Shared async executor with MDC propagation.
     * Named "taskExecutor" so Spring picks it up as the default @Async executor.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
```

**Remove `@EnableAsync`** from all three service application classes — `AsyncConfig` in `common`
handles it once for all services that include `common` on the classpath.

### Step 3 — Propagate MDC into Kafka listener threads

Kafka listeners run on their own thread pool managed by the container factory, not Spring's
`@Async` executor. A `RecordInterceptor` is the right hook point.

**File**: `common/src/main/java/com/demo/common/config/KafkaDlqConfig.java` — add:

```java
/**
 * Extracts the correlation ID from the Kafka record header (if present) and puts it in MDC
 * before the listener method runs. Falls back to a fresh UUID if no header exists.
 * Clears MDC after the record is processed to prevent leaking into the next record's thread.
 */
@Bean
public RecordInterceptor<String, String> mdcRecordInterceptor() {
    return new RecordInterceptor<>() {
        private static final String HEADER_CORRELATION_ID = "correlationId";

        @Override
        public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
                                                         Consumer<String, String> consumer) {
            Header header = record.headers().lastHeader(HEADER_CORRELATION_ID);
            String correlationId = header != null
                    ? new String(header.value(), StandardCharsets.UTF_8)
                    : UUID.randomUUID().toString();
            MDC.put("requestId", correlationId);
            MDC.put("kafkaTopic", record.topic());
            MDC.put("kafkaPartition", String.valueOf(record.partition()));
            return record;
        }

        @Override
        public void afterRecord(ConsumerRecord<String, String> record,
                                Consumer<String, String> consumer) {
            MDC.clear();
        }
    };
}
```

Wire the interceptor into all `KafkaListenerContainerFactory` beans by calling
`.setRecordInterceptor(mdcRecordInterceptor)` in each service's `KafkaConsumerConfig`.

### Step 4 — Propagate `correlationId` from outbox events (optional, high value)

When `OutboxPublisher` sends a Kafka record, include the originating `requestId` from MDC as a
Kafka header named `correlationId`. This allows the header to survive the full
HTTP → DB → Outbox → Kafka → Consumer path, making cross-service trace correlation
possible without Zipkin.

```java
// OutboxPublisher.java — add header to each send:
kafkaTemplate.send(new ProducerRecord<>(
    event.getTopic(),
    null,           // partition
    event.getAggregateId().toString(),
    event.getPayload(),
    List.of(new RecordHeader("correlationId",
            Optional.ofNullable(MDC.get("requestId"))
                    .orElse(event.getId().toString())
                    .getBytes(StandardCharsets.UTF_8)))
));
```

### Step 5 — Tests

1. **`@Async` propagation test** (`task-service` IT):
   - Set `MDC.put("requestId", "test-request-123")` before calling an `@Async` method
   - Inside the async method, assert `MDC.get("requestId").equals("test-request-123")`

2. **Kafka listener MDC test** (`audit-service` or `notification-service` IT):
   - Produce a record with header `correlationId: test-trace-xyz`
   - Assert `MDC.get("requestId")` equals `test-trace-xyz` inside the listener

## Files to Create / Modify

```
common/src/main/java/com/demo/common/web/MdcTaskDecorator.java                  NEW
common/src/main/java/com/demo/common/config/AsyncConfig.java                    NEW
common/src/main/java/com/demo/common/config/KafkaDlqConfig.java                 ADD RecordInterceptor bean
task-service/src/main/java/com/demo/task/outbox/OutboxPublisher.java            ADD correlationId header (Step 4)
task-service/src/main/java/com/demo/task/TaskServiceApplication.java            REMOVE @EnableAsync
notification-service/.../NotificationServiceApplication.java                    REMOVE @EnableAsync
user-service/.../UserServiceApplication.java                                    REMOVE @EnableAsync
audit-service/.../config/KafkaConsumerConfig.java                               WIRE RecordInterceptor
notification-service/.../config/KafkaConsumerConfig.java                        WIRE RecordInterceptor
search-service/.../config/KafkaConsumerConfig.java                              WIRE RecordInterceptor
Each affected service *IT.java                                                  ADD MDC propagation test
```

## Verification

```bash
# 1. Start services, call POST /tasks (triggers async phase creation)
# 2. In Kibana/log output: find log lines from TaskPhaseService.createDefaultPhasesForProject
#    → requestId should match the HTTP request's requestId

# 3. Publish a Kafka event manually with correlationId header
# 4. In consumer log lines: requestId should match the header value
```

## Execution Notes

- `AsyncConfig` in `common` overrides all per-service `@EnableAsync` — remove duplicates to avoid
  multiple executor beans competing for the `"taskExecutor"` name
- `ThreadPoolTaskExecutor` pool sizes (4 core, 20 max, 200 queue) are sensible defaults; tune per
  service's async load profile in `application.yml` if needed
- Recommended branch: `fix_mdc_async_propagation`

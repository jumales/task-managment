# Memory Leak Findings — Long-Running Services

Analysis of all microservices for memory leaks that manifest over hours/days in production.

---

## Priority 1 — HIGH: Unbounded `processed_kafka_event` table

**Services:** notification-service, audit-service, reporting-service  
**Files:** `*/dedup/ProcessedEventService.java`, `*/dedup/ProcessedEventRepository.java`

Every Kafka message inserts a dedup row. No cleanup scheduler exists. At production load
this table grows to millions of rows over weeks. `OutboxCleanupService` in task-service
already demonstrates the correct pattern — it is missing from all consumer services.

**Fix:**
1. Add `deleteOlderThan(Instant cutoff)` to each `ProcessedEventRepository`
2. Add `ProcessedEventCleanupScheduler` `@Scheduled(cron = "0 0 2 * * *")` to each service
3. Retention window: 30 days (covers any reasonable Kafka replay window)

---

## Priority 2 — HIGH: MinIO InputStream not closed on client disconnect

**Service:** file-service  
**File:** `file-service/src/main/java/com/demo/file/service/FileService.java:112`

```java
InputStream stream = minioClient.getObject(...);
return new DownloadResult(new InputStreamResource(stream), ...);
```

If client disconnects before download completes, the InputStream is never closed,
leaking MinIO HTTP connections. Over hours of partial downloads, the connection pool exhausts.

**Fix:** Use `StreamingResponseBody` with try-with-resources in the controller.

---

## Priority 3 — HIGH: OutboxPublisher no batch limit

**Service:** task-service  
**File:** `task-service/src/main/java/com/demo/task/outbox/OutboxPublisher.java:62`

`findUnpublishedForUpdate()` has no LIMIT. If outbox cleanup lags, one poll cycle loads
100k+ events + 100k CompletableFuture objects into heap.

**Fix:** Add `Pageable.ofSize(1000)` to `findUnpublishedForUpdate`.

---

## Priority 4 — HIGH: ReindexService loads ALL data into memory

**Service:** search-service  
**File:** `search-service/src/main/java/com/demo/search/service/ReindexService.java:88`

```java
List<UserDto> all = new ArrayList<>();
do { all.addAll(response.getContent()); } while (!response.isLast());
```

1M users × 2KB each ≈ 2GB ArrayList before indexing starts. OOM risk.

**Fix:** Index each page immediately; discard before fetching next page.

---

## Priority 5 — MEDIUM: Redis no `maxmemory` policy

**Services:** task-service, user-service  
**Files:** `*/config/CacheConfig.java`

TTL=10min is set per-entry, but Redis itself has no `maxmemory` ceiling.
In a system with many unique cache keys the Redis process grows unbounded until OOM.

**Fix (redis.conf / docker-compose env):**
```
maxmemory 256mb
maxmemory-policy allkeys-lru
```

---

## Priority 6 — MEDIUM: FileCleanupScheduler no pagination

**Service:** file-service  
**File:** `file-service/src/main/java/com/demo/file/scheduler/FileCleanupScheduler.java:40`

Daily cleanup loads ALL expired files with no LIMIT. Could be 100k+ rows in one query.

**Fix:** Loop with `findExpiredDeletedFiles(cutoff, PageRequest.of(0, 100))`.

---

## Priority 7 — MEDIUM: AsyncConfig no graceful shutdown

**Module:** common  
**File:** `common/src/main/java/com/demo/common/config/AsyncConfig.java:24`

`ThreadPoolTaskExecutor` missing graceful shutdown config. Queued async tasks are lost on SIGTERM.

**Fix:**
```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(10);
```

---

## Priority 8 — LOW-MEDIUM: Kafka consumer poll interval not explicit

**Service:** notification-service  
**File:** `notification-service/src/main/resources/application.yml`

Email sends can take 10–30s. Default `max.poll.interval.ms=300s` covers this but is not
explicitly configured — a library upgrade or config override could silently lower it,
causing rebalance + duplicate notifications.

**Fix:**
```yaml
spring.kafka.consumer:
  max-poll-interval-ms: 300000
  session-timeout-ms: 45000
```

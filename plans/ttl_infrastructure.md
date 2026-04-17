# TTL — Infrastructure Plan

## Context
Beyond the database, three infrastructure systems accumulate data without expiry:
- Kafka topics retain messages indefinitely by default
- Elasticsearch indices (via Logstash) grow without bound
- MinIO stores deleted file objects until manually purged

This plan configures TTL for each. All retention values are configurable in `config-repo/application.yml`
under `ttl.*` (see `ttl_db_archiving.md` for the full property list).

---

## Part A — Kafka Topic Retention

### Approach
Configure per-topic `retention.ms` via Spring `NewTopic` beans in task-service on startup.
`NewTopic` is idempotent: if the topic exists, Spring AdminClient updates its config.

### File to Modify
`task-service/.../config/KafkaTopicConfig.java` (create or modify existing)

```java
@Bean
NewTopic taskEventsTopic() {
    return TopicBuilder.name(KafkaTopics.TASK_EVENTS)
        .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(Duration.ofHours(
                    ttlProperties.getKafka().getTaskEventsRetentionHours()).toMillis()))
        .build();
}

@Bean
NewTopic taskChangedTopic() {
    return TopicBuilder.name(KafkaTopics.TASK_CHANGED)
        .config(TopicConfig.RETENTION_MS_CONFIG,
                String.valueOf(Duration.ofHours(
                    ttlProperties.getKafka().getTaskChangedRetentionHours()).toMillis()))
        .build();
}
```

### Config properties (config-repo/application.yml)
```yaml
ttl:
  kafka:
    task-events-retention-hours: 168    # 7 days
    task-changed-retention-hours: 168   # 7 days
```

### Verification
```bash
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic task-events
# Confirm: retention.ms=604800000
```

---

## Part B — Elasticsearch Log ILM

### Approach
Bootstrap an ILM (Index Lifecycle Management) policy and index template on search-service startup
using `ElasticsearchClient`. PUT operations are idempotent — safe to run on every startup.

The policy applies to all `logstash-*` indices created by Logstash.

### File to Create
`search-service/.../config/ElasticsearchIlmConfig.java`

```java
/**
 * Bootstraps Elasticsearch ILM policy for Logstash log indices on startup.
 * Applies ttl.elasticsearch.log-retention-days as the delete trigger age.
 */
@Configuration
public class ElasticsearchIlmConfig {

    @PostConstruct
    void applyIlmPolicy() {
        // PUT _ilm/policy/logstash-ttl-policy
        // {
        //   "policy": {
        //     "phases": {
        //       "hot":    { "min_age": "0ms", "actions": {} },
        //       "delete": { "min_age": "{N}d", "actions": { "delete": {} } }
        //     }
        //   }
        // }
        //
        // PUT _index_template/logstash-ttl-template
        // {
        //   "index_patterns": ["logstash-*"],
        //   "template": {
        //     "settings": { "index.lifecycle.name": "logstash-ttl-policy" }
        //   }
        // }
    }
}
```

Note: Only applies to **new** indices created after the template is set. Existing indices must be
manually associated with the policy via Kibana or `_ilm/move` API.

### Config property
```yaml
ttl:
  elasticsearch:
    log-retention-days: 30
```

### Verification
```
GET _ilm/policy/logstash-ttl-policy
GET _index_template/logstash-ttl-template
# Check Kibana Stack Management → Index Lifecycle Policies
```

---

## Part C — MinIO File Object Lifecycle

### Approach
MinIO's native lifecycle policies cannot target objects based on database state (e.g., soft-deleted
records). Instead, a scheduled Spring job in file-service handles this:

1. Query `file_metadata` for rows where `deleted_at < NOW() - retention`
2. Delete the MinIO object
3. Hard-delete the `file_metadata` row

This job is also the mechanism that cleans up MinIO objects for task-archived files
(see `ttl_db_archiving.md` — file-service listener sets `deleted_at` on TASK_ARCHIVED event,
then this scheduler purges the objects after the TTL window).

### File to Create
`file-service/.../scheduler/FileCleanupScheduler.java`

```java
/**
 * Purges soft-deleted file records and their corresponding MinIO objects after TTL expires.
 * Triggered by task archiving (via TASK_ARCHIVED event) and direct file deletion by users.
 */
@Component
public class FileCleanupScheduler {

    @Scheduled(cron = "0 6 * * *")
    @Transactional
    public void cleanup() {
        List<FileMetadata> expired = fileMetadataRepository
            .findByDeletedAtBefore(Instant.now().minus(retentionDays, DAYS));
        for (FileMetadata file : expired) {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(file.getBucket())
                    .object(file.getObjectKey())
                    .build());
            fileMetadataRepository.delete(file);
        }
    }
}
```

### Repository method needed
`FileMetadataRepository`:
```java
List<FileMetadata> findByDeletedAtBefore(Instant cutoff);
```

### Config property
```yaml
ttl:
  file:
    deleted-object-retention-days: 30
```

### Verification
1. Upload a file, then soft-delete it (or trigger task archive for a task with attachments)
2. Confirm `file_metadata.deleted_at` is set
3. Run `FileCleanupScheduler` with retention = 0 days (test override)
4. Confirm MinIO object no longer exists (check MinIO console at localhost:9001)
5. Confirm `file_metadata` row deleted

---

## Part D — DB Queue Table Cleanup

Handled in task-service (see `ttl_db_archiving.md`, Part B3).

Summary:
- `outbox_events`: delete `published = TRUE AND created_at < cutoff` nightly
- `task_code_jobs`: delete `processed_at IS NOT NULL AND created_at < cutoff` nightly

These are in-DB cleanup jobs, not infrastructure-level — listed here for completeness.

---

## docker-compose.yml Considerations

No changes required for Kafka retention — the `NewTopic` beans configure it at the broker level
on first startup. However, Kafka's `KAFKA_LOG_RETENTION_HOURS` env var sets the **broker default**
for any topic that doesn't have an explicit config. Optionally add to docker-compose.yml:

```yaml
kafka:
  environment:
    KAFKA_LOG_RETENTION_HOURS: 168   # 7 days default; topic-level config overrides this
```

---

## Implementation Sequence

```
1. task-service    — KafkaTopicConfig: NewTopic beans with retention.ms
2. search-service  — ElasticsearchIlmConfig: bootstrap ILM policy + index template
3. file-service    — FileCleanupScheduler + FileMetadataRepository.findByDeletedAtBefore()
4. config-repo     — ttl.kafka.*, ttl.elasticsearch.*, ttl.file.* in application.yml
5. docker-compose  — (optional) KAFKA_LOG_RETENTION_HOURS default
```

---

## Critical Files

| File | Action |
|---|---|
| `task-service/.../config/KafkaTopicConfig.java` | Create or modify |
| `search-service/.../config/ElasticsearchIlmConfig.java` | Create |
| `file-service/.../scheduler/FileCleanupScheduler.java` | Create |
| `file-service/.../repository/FileMetadataRepository.java` | Modify (add findByDeletedAtBefore) |
| `config-repo/application.yml` | Modify (ttl.kafka.*, ttl.elasticsearch.*, ttl.file.*) |
| `docker-compose.yml` | Modify (optional Kafka default retention) |

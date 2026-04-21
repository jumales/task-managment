package com.demo.audit.consumer;

import com.demo.audit.archive.AuditArchiveService;
import com.demo.audit.dedup.ProcessedEventService;
import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes task lifecycle events from the {@code task-events} topic.
 * Currently handles only {@link com.demo.common.event.TaskEventType#ARCHIVED} events,
 * which trigger archiving of all audit records for the task.
 * Other event types (CREATED, UPDATED, DELETED) are intentionally ignored by audit-service.
 */
@Component
public class TaskLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskLifecycleConsumer.class);

    /** Kafka consumer group — paired with the {@link #consume} @KafkaListener groupId. */
    private static final String CONSUMER_GROUP = "audit-lifecycle-group";

    private final AuditArchiveService archiveService;
    private final ObjectMapper objectMapper;
    private final ProcessedEventService processedEventService;

    public TaskLifecycleConsumer(AuditArchiveService archiveService,
                                 ObjectMapper objectMapper,
                                 ProcessedEventService processedEventService) {
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
        this.processedEventService = processedEventService;
    }

    /**
     * Receives a task lifecycle event and routes ARCHIVED events to the audit archive service.
     * Non-ARCHIVED events are skipped — audit-service does not maintain a task projection.
     *
     * <p>Idempotency: {@link ProcessedEventService#markProcessed} ensures each event's archive
     * work runs at most once across crash-recovery or duplicate deliveries from the outbox publisher.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT immediately
     */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = CONSUMER_GROUP, concurrency = "3")
    public void consume(String message) throws JsonProcessingException {
        TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
        log.debug("Received TaskEvent: task={} type={}", event.getTaskId(), event.getEventType());

        if (event.getEventId() != null
                && !processedEventService.markProcessed(event.getEventId(), CONSUMER_GROUP)) {
            log.info("Duplicate event {} — skipping", event.getEventId());
            return;
        }

        switch (event.getEventType()) {
            case ARCHIVED -> archiveService.archiveTask(event.getTaskId(), event.getArchiveMonth());
            default -> { /* CREATED, UPDATED, DELETED are not relevant to audit archiving */ }
        }
    }
}

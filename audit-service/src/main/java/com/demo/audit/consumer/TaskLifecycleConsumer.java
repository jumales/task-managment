package com.demo.audit.consumer;

import com.demo.audit.archive.AuditArchiveService;
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

    private final AuditArchiveService archiveService;
    private final ObjectMapper objectMapper;

    public TaskLifecycleConsumer(AuditArchiveService archiveService, ObjectMapper objectMapper) {
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a task lifecycle event and routes ARCHIVED events to the audit archive service.
     * Non-ARCHIVED events are skipped — audit-service does not maintain a task projection.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT immediately
     */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = "audit-lifecycle-group", concurrency = "3")
    public void consume(String message) throws JsonProcessingException {
        TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
        log.debug("Received TaskEvent: task={} type={}", event.getTaskId(), event.getEventType());

        switch (event.getEventType()) {
            case ARCHIVED -> archiveService.archiveTask(event.getTaskId(), event.getArchiveMonth());
            default -> { /* CREATED, UPDATED, DELETED are not relevant to audit archiving */ }
        }
    }
}

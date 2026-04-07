package com.demo.task.outbox;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.task.model.OutboxEvent;
import com.demo.task.model.OutboxEventType;
import com.demo.task.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Shared helper that serializes a {@link TaskChangedEvent} and saves it to the outbox table
 * within the current transaction. Eliminates the duplicate {@code writeToOutbox} method that
 * previously existed in {@code TaskService} and {@code TaskWorkLogService}.
 */
@Component
public class OutboxWriter {

    private static final String AGGREGATE_TYPE = "Task";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the event to JSON and persists it as an unpublished outbox entry.
     * Must be called inside an active transaction so the write is atomic with the business operation.
     *
     * @throws RuntimeException wrapping {@link JsonProcessingException} if serialization fails
     */
    public void write(TaskChangedEvent event) {
        try {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(event.getTaskId())
                    .eventType(OutboxEventType.TASK_CHANGED)
                    .topic(KafkaTopics.TASK_CHANGED)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}

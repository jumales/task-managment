package com.demo.task.outbox;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.event.TaskEvent;
import com.demo.task.model.OutboxAggregateType;
import com.demo.task.model.OutboxEvent;
import com.demo.task.model.OutboxEventType;
import com.demo.task.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared helper that serializes task outbox events and saves them to the outbox table
 * within the current transaction. Handles both {@link TaskChangedEvent} (task-changed topic)
 * and {@link TaskEvent} (task-events topic) writes.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the event to JSON and persists it as an unpublished outbox entry on the
     * {@code task-changed} topic. Must be called inside an active transaction.
     *
     * @throws RuntimeException wrapping {@link JsonProcessingException} if serialization fails
     */
    public void write(TaskChangedEvent event) {
        try {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType(OutboxAggregateType.TASK)
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

    /**
     * Serializes the archive event to JSON and persists it as an unpublished outbox entry on
     * the {@code task-events} topic. Must be called inside an active transaction.
     *
     * @param event the fully-populated archived event (taskId, archiveMonth, archivedFileIds)
     * @throws RuntimeException wrapping {@link JsonProcessingException} if serialization fails
     */
    public void writeArchivedEvent(TaskEvent event) {
        writeTaskEvent(event.getTaskId(), OutboxEventType.TASK_ARCHIVED, event);
    }

    /**
     * Serializes the lifecycle event to JSON and persists it as an unpublished outbox entry on
     * the {@code task-events} topic. Must be called inside an active transaction.
     *
     * @param taskId    the task being described (used as the aggregate id)
     * @param eventType must be one of {@link OutboxEventType#TASK_CREATED},
     *                  {@link OutboxEventType#TASK_UPDATED}, or {@link OutboxEventType#TASK_DELETED}
     * @param event     the fully-populated lifecycle event to publish
     * @throws RuntimeException wrapping {@link JsonProcessingException} if serialization fails
     */
    public void writeTaskEvent(UUID taskId, OutboxEventType eventType, TaskEvent event) {
        try {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType(OutboxAggregateType.TASK)
                    .aggregateId(taskId)
                    .eventType(eventType)
                    .topic(KafkaTopics.TASK_EVENTS)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task lifecycle outbox event", e);
        }
    }
}

package com.demo.search.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskEvent;
import com.demo.search.service.TaskIndexService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes task lifecycle events from the {@code task-events} Kafka topic and
 * keeps the Elasticsearch task index in sync.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 *
 * <p>Idempotency: no dedup table is needed — indexing by {@code taskId} is an upsert
 * (Elasticsearch replaces the document with the same ID) and delete-by-ID is a no-op on the
 * second call. Duplicate events from the outbox publisher converge to the same index state.
 */
@Component
public class TaskEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventConsumer.class);

    private final TaskIndexService indexService;
    private final ObjectMapper objectMapper;

    public TaskEventConsumer(TaskIndexService indexService, ObjectMapper objectMapper) {
        this.indexService = indexService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a raw JSON task event and routes it to the appropriate index operation.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT immediately
     */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = "search-group", concurrency = "3")
    public void consume(String message) throws JsonProcessingException {
        TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
        log.info("Received TaskEvent: task={} type={}", event.getTaskId(), event.getEventType());

        switch (event.getEventType()) {
            case CREATED, UPDATED -> indexService.index(event);
            case DELETED, ARCHIVED -> indexService.delete(event);
        }
    }
}

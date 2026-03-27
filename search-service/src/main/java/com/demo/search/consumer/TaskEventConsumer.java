package com.demo.search.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskEvent;
import com.demo.search.service.TaskIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes task lifecycle events from the {@code task-events} Kafka topic and
 * keeps the Elasticsearch task index in sync.
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

    /** Receives a raw JSON task event and routes it to the appropriate index operation. */
    @KafkaListener(topics = KafkaTopics.TASK_EVENTS, groupId = "search-group", concurrency = "3")
    public void consume(String message) {
        try {
            TaskEvent event = objectMapper.readValue(message, TaskEvent.class);
            log.info("Received TaskEvent: task={} type={}", event.getTaskId(), event.getEventType());

            switch (event.getEventType()) {
                case CREATED, UPDATED -> indexService.index(event);
                case DELETED           -> indexService.delete(event);
            }
        } catch (Exception e) {
            log.error("Failed to process task event: {}", e.getMessage(), e);
        }
    }
}

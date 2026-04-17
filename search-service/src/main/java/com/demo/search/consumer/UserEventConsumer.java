package com.demo.search.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.UserEvent;
import com.demo.search.service.UserIndexService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes user lifecycle events from the {@code user-events} Kafka topic and
 * keeps the Elasticsearch user index in sync.
 * Exceptions propagate to {@code DefaultErrorHandler} for bounded retry and DLT forwarding.
 */
@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final UserIndexService indexService;
    private final ObjectMapper objectMapper;

    public UserEventConsumer(UserIndexService indexService, ObjectMapper objectMapper) {
        this.indexService = indexService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives a raw JSON user event and routes it to the appropriate index operation.
     *
     * @throws JsonProcessingException if the message cannot be deserialized — propagates to DLT immediately
     */
    @KafkaListener(topics = KafkaTopics.USER_EVENTS, groupId = "search-group", concurrency = "3")
    public void consume(String message) throws JsonProcessingException {
        UserEvent event = objectMapper.readValue(message, UserEvent.class);
        log.info("Received UserEvent: user={} type={}", event.getUserId(), event.getEventType());

        switch (event.getEventType()) {
            case CREATED, UPDATED -> indexService.index(event);
            case DELETED           -> indexService.delete(event);
        }
    }
}

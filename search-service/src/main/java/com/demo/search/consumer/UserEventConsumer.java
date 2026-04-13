package com.demo.search.consumer;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.UserEvent;
import com.demo.search.service.UserIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes user lifecycle events from the {@code user-events} Kafka topic and
 * keeps the Elasticsearch user index in sync.
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

    /** Receives a raw JSON user event and routes it to the appropriate index operation. */
    @KafkaListener(topics = KafkaTopics.USER_EVENTS, groupId = "search-group", concurrency = "3")
    public void consume(String message, Acknowledgment ack) {
        try {
            UserEvent event = objectMapper.readValue(message, UserEvent.class);
            log.info("Received UserEvent: user={} type={}", event.getUserId(), event.getEventType());

            switch (event.getEventType()) {
                case CREATED, UPDATED -> indexService.index(event);
                case DELETED           -> indexService.delete(event);
            }
            ack.acknowledge(); // commit offset only after successful index operation
        } catch (Exception e) {
            log.error("Failed to process user event: {}", e.getMessage(), e);
            // Do not acknowledge — offset not committed, message will be retried
        }
    }
}

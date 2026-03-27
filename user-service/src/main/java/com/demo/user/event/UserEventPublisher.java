package com.demo.user.event;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.UserEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes user lifecycle events to the {@code user-events} Kafka topic.
 * Consumed by search-service to keep the Elasticsearch user index current.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public UserEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** Publishes the given event to the {@code user-events} topic. Logs and swallows on failure to avoid rolling back the DB transaction. */
    public void publish(UserEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.USER_EVENTS, event.getUserId().toString(), payload);
            log.info("Published {} event for user {}", event.getEventType(), event.getUserId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize UserEvent for user {}: {}", event.getUserId(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to publish UserEvent for user {}: {}", event.getUserId(), e.getMessage());
        }
    }

    /** Convenience factory — publishes a CREATED event. */
    public void publishCreated(UUID userId, String name, String email, String username, boolean active) {
        publish(UserEvent.created(userId, name, email, username, active));
    }

    /** Convenience factory — publishes an UPDATED event. */
    public void publishUpdated(UUID userId, String name, String email, String username, boolean active) {
        publish(UserEvent.updated(userId, name, email, username, active));
    }

    /** Convenience factory — publishes a DELETED event. */
    public void publishDeleted(UUID userId) {
        publish(UserEvent.deleted(userId));
    }
}

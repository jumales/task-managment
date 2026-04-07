package com.demo.user.event;

import com.demo.common.config.KafkaTopics;
import com.demo.common.dto.UserDto;
import com.demo.common.event.UserEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes user lifecycle events directly to the {@value KafkaTopics#USER_EVENTS} Kafka topic.
 * Unlike the former outbox-based publisher, this sends messages in-process without a DB-backed
 * outbox table. The at-most-once trade-off is accepted for the dev environment.
 *
 * <p>Consumed by search-service to keep the Elasticsearch user index current.
 */
@Component
public class KafkaUserEventPublisher implements UserEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaUserEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaUserEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** Publishes a CREATED event for the newly registered user. */
    public void publishCreated(UserDto user) {
        publish(UserEvent.created(user.getId(), user.getName(), user.getEmail(),
                user.getUsername(), user.isActive()));
    }

    /** Publishes an UPDATED event reflecting the new state of the user. */
    public void publishUpdated(UserDto user) {
        publish(UserEvent.updated(user.getId(), user.getName(), user.getEmail(),
                user.getUsername(), user.isActive()));
    }

    /** Publishes a DELETED event carrying only the user ID (other fields are null). */
    public void publishDeleted(UUID userId) {
        publish(UserEvent.deleted(userId));
    }

    /**
     * Serializes the event and sends it to Kafka asynchronously.
     * The user ID is used as the message key to preserve ordering per user.
     * Logs a warning if the send fails (e.g. broker unavailable).
     */
    private void publish(UserEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.USER_EVENTS, event.getUserId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish {} event for user {}: {}",
                                    event.getEventType(), event.getUserId(), ex.getMessage());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize UserEvent for user {}", event.getUserId(), e);
        }
    }
}

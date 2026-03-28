package com.demo.user.outbox;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.UserEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Serializes a {@link UserEvent} and saves it to the outbox table within the current transaction.
 * The event is forwarded to Kafka by the scheduled {@link UserOutboxPublisher},
 * guaranteeing at-least-once delivery even if the Kafka broker is temporarily unavailable.
 */
@Component
public class UserOutboxWriter {

    private static final String AGGREGATE_TYPE = "User";

    private final UserOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public UserOutboxWriter(UserOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the event as an unpublished outbox record.
     * Must be called inside an active transaction so the write is atomic with the business save.
     *
     * @throws RuntimeException wrapping {@link JsonProcessingException} if serialization fails
     */
    public void write(UserEvent event) {
        try {
            repository.save(UserOutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(event.getUserId())
                    .eventType(UserOutboxEventType.USER_CHANGED)
                    .topic(KafkaTopics.USER_EVENTS)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize user outbox event", e);
        }
    }
}

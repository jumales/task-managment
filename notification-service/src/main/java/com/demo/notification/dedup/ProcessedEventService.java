package com.demo.notification.dedup;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Guards Kafka consumers against duplicate event processing.
 * Uses an atomic INSERT with a unique constraint to detect and reject already-seen events.
 */
@Service
public class ProcessedEventService {

    /** Kafka consumer group ID for notification-service; must match the @KafkaListener groupId. */
    public static final String CONSUMER_GROUP = "notification-group";

    private final ProcessedEventRepository repository;

    public ProcessedEventService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Attempts to mark the given event as processed. Returns {@code true} if the event is new,
     * {@code false} if it was already processed (duplicate delivery).
     * Runs in its own transaction so the dedup record commits before any async work begins.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessed(UUID eventId, String consumerGroup) {
        try {
            repository.save(ProcessedKafkaEvent.builder()
                    .eventId(eventId)
                    .consumerGroup(consumerGroup)
                    .processedAt(Instant.now())
                    .build());
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}

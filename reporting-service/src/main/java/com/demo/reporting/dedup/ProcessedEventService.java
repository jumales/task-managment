package com.demo.reporting.dedup;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Guards Kafka consumers against duplicate event processing.
 * Uses a check-then-insert pattern; the DB unique index is defense-in-depth only.
 */
@Service
public class ProcessedEventService {

    private final ProcessedEventRepository repository;

    public ProcessedEventService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Attempts to mark the given event as processed. Returns {@code true} if the event is new,
     * {@code false} if it was already processed (duplicate delivery).
     *
     * <p>Checks existence first (a single partition is assigned to a single consumer thread, so
     * same-eventId retries are serialized). The saveAndFlush + catch is a safety net for the rare
     * cross-instance race on the same partition during a rebalance.
     *
     * <p>Runs in its own transaction so the dedup record commits independently from any downstream work.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = DataIntegrityViolationException.class)
    public boolean markProcessed(UUID eventId, String consumerGroup) {
        if (repository.existsByEventIdAndConsumerGroup(eventId, consumerGroup)) {
            return false;
        }
        try {
            repository.saveAndFlush(ProcessedKafkaEvent.builder()
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

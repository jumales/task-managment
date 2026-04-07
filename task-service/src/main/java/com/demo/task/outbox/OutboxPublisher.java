package com.demo.task.outbox;

import com.demo.task.model.OutboxEvent;
import com.demo.task.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls the outbox table on a fixed schedule and publishes unpublished events to Kafka.
 * Uses a raw {@code String} Kafka template so it can forward any JSON payload to any topic
 * without per-type deserialization — the consumer is responsible for mapping the JSON.
 * Marks each event as published after successful delivery.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Polls unpublished outbox events every 5 seconds and forwards them to Kafka. */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findByPublishedFalse();
        List<OutboxEvent> published = new ArrayList<>();
        for (OutboxEvent event : pending) {
            try {
                // Forward the raw JSON payload to the topic recorded when the event was written.
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
                event.setPublished(true);
                published.add(event);
                log.info("Published {} event {} for aggregate {}", event.getEventType(), event.getId(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}, will retry: {}", event.getId(), e.getMessage());
            }
        }
        // Batch-persist all successfully published events in a single UPDATE.
        if (!published.isEmpty()) {
            outboxRepository.saveAll(published);
        }
    }
}

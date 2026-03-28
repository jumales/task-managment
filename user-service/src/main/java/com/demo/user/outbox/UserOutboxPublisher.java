package com.demo.user.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls the user outbox table on a fixed schedule and publishes unpublished events to Kafka.
 * Marks each event as published after successful delivery.
 * Uses a raw {@code String} Kafka template to forward the stored JSON payload without re-serializing.
 */
@Component
public class UserOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserOutboxPublisher.class);

    private final UserOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public UserOutboxPublisher(UserOutboxRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Polls unpublished user outbox events every 5 seconds and forwards them to Kafka. */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<UserOutboxEvent> pending = outboxRepository.findByPublishedFalse();
        List<UserOutboxEvent> published = new ArrayList<>();
        for (UserOutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
                event.setPublished(true);
                published.add(event);
                log.info("Published {} event {} for user {}", event.getEventType(), event.getId(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish user outbox event {}, will retry: {}", event.getId(), e.getMessage());
            }
        }
        // Batch-persist all successfully published events in a single UPDATE.
        if (!published.isEmpty()) {
            outboxRepository.saveAll(published);
        }
    }
}

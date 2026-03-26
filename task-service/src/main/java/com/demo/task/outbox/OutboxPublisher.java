package com.demo.task.outbox;

import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.task.model.OutboxEvent;
import com.demo.task.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** Single Kafka topic for all task change events. */
    public static final String TOPIC = KafkaTopics.TASK_CHANGED;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, TaskChangedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, TaskChangedEvent> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** Polls unpublished outbox events every 5 seconds and forwards them to Kafka. */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findByPublishedFalse();
        for (OutboxEvent event : pending) {
            try {
                TaskChangedEvent payload = objectMapper.readValue(event.getPayload(), TaskChangedEvent.class);
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), payload);
                event.setPublished(true);
                outboxRepository.save(event);
                log.info("Published {} event {} for task {}", payload.getChangeType(), event.getId(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}, will retry: {}", event.getId(), e.getMessage());
            }
        }
    }
}

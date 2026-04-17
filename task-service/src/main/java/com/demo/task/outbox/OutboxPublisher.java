package com.demo.task.outbox;

import com.demo.task.model.OutboxEvent;
import com.demo.task.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    /**
     * Polls unpublished outbox events every second and forwards them to Kafka in parallel.
     * All sends are fired concurrently so the Kafka producer can batch them into a single
     * network round-trip instead of blocking on each acknowledgement sequentially.
     * Each send is awaited with a 10-second deadline; events that miss the deadline are
     * left unpublished and retried on the next poll.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findUnpublishedForUpdate();
        if (pending.isEmpty()) return;

        // Fire all Kafka sends concurrently — KafkaTemplate.send() is non-blocking;
        // the producer batches them on its internal I/O thread.
        // correlationId header carries the originating requestId through the full
        // HTTP → Outbox → Kafka → Consumer trace path without requiring Zipkin.
        String correlationId = Optional.ofNullable(MDC.get("requestId"))
                .orElse(java.util.UUID.randomUUID().toString());
        List<CompletableFuture<SendResult<String, String>>> futures = pending.stream()
                .map(event -> kafkaTemplate.send(new ProducerRecord<>(
                        event.getTopic(),
                        null,
                        event.getAggregateId().toString(),
                        event.getPayload(),
                        List.of(new RecordHeader("correlationId",
                                correlationId.getBytes(StandardCharsets.UTF_8))))))
                .toList();

        // Collect results: mark each event published only after its send succeeds.
        List<OutboxEvent> published = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            OutboxEvent event = pending.get(i);
            try {
                futures.get(i).get(10, TimeUnit.SECONDS);
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

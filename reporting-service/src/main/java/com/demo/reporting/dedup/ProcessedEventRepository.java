package com.demo.reporting.dedup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repository for tracking processed Kafka event IDs to enable idempotent consumption. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedKafkaEvent, UUID> {

    boolean existsByEventIdAndConsumerGroup(UUID eventId, String consumerGroup);
}

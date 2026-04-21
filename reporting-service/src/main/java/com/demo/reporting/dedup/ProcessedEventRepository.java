package com.demo.reporting.dedup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Repository for tracking processed Kafka event IDs to enable idempotent consumption. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedKafkaEvent, UUID> {

    boolean existsByEventIdAndConsumerGroup(UUID eventId, String consumerGroup);

    /** Deletes dedup records older than {@code cutoff} to prevent unbounded table growth. */
    @Modifying
    @Query("DELETE FROM ProcessedKafkaEvent e WHERE e.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

package com.demo.notification.dedup;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a Kafka event that has been successfully processed by this consumer.
 * Used to detect and skip duplicate deliveries.
 */
@Entity
@Table(name = "processed_kafka_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedKafkaEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID eventId;
    private String consumerGroup;
    private Instant processedAt;
}

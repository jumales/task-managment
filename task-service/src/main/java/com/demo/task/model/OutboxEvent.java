package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String aggregateType;  // e.g. "Task"
    private UUID aggregateId;
    @Enumerated(EnumType.STRING)
    private OutboxEventType eventType;

    private String topic;          // Kafka topic to publish to

    @Column(columnDefinition = "TEXT")
    private String payload;        // JSON-serialized event

    private boolean published;

    private Instant createdAt;
}

package com.demo.user.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Outbox record for user lifecycle events, published to Kafka by {@link UserOutboxPublisher}. */
@Entity
@Table(name = "user_outbox_events")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Domain type, always {@code "User"}. */
    private String aggregateType;

    /** UUID of the affected user. */
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    private UserOutboxEventType eventType;

    /** Kafka topic to publish to — typically {@code user-events}. */
    private String topic;

    /** JSON-serialized {@link com.demo.common.event.UserEvent}. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    private boolean published;

    private Instant createdAt;
}

package com.demo.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Lifecycle event published to the {@code user-events} Kafka topic whenever a user is
 * created, updated, or deleted. Consumed by search-service to keep the Elasticsearch index current.
 *
 * <p>For {@link UserEventType#DELETED} events only {@code userId} and {@code eventType} are set;
 * all other fields are {@code null}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEvent {

    private UUID userId;
    private UserEventType eventType;
    private Instant timestamp;

    /** Populated for CREATED and UPDATED events. */
    private String name;
    private String email;
    private String username;
    private boolean active;

    /** Factory for a user-created event. */
    public static UserEvent created(UUID userId, String name, String email, String username, boolean active) {
        return full(UserEventType.CREATED, userId, name, email, username, active);
    }

    /** Factory for a user-updated event. */
    public static UserEvent updated(UUID userId, String name, String email, String username, boolean active) {
        return full(UserEventType.UPDATED, userId, name, email, username, active);
    }

    /** Factory for a user-deleted event carrying only the user ID. */
    public static UserEvent deleted(UUID userId) {
        UserEvent e = new UserEvent();
        e.userId = userId;
        e.eventType = UserEventType.DELETED;
        e.timestamp = Instant.now();
        return e;
    }

    private static UserEvent full(UserEventType type, UUID userId, String name,
                                   String email, String username, boolean active) {
        UserEvent e = new UserEvent();
        e.userId = userId;
        e.eventType = type;
        e.timestamp = Instant.now();
        e.name = name;
        e.email = email;
        e.username = username;
        e.active = active;
        return e;
    }
}

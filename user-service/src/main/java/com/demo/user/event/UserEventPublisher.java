package com.demo.user.event;

import com.demo.common.event.UserEvent;
import com.demo.user.outbox.UserOutboxWriter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes user lifecycle events to the transactional outbox for reliable, at-least-once
 * delivery to Kafka. The outbox write is atomic with the business operation — either both
 * commit or both roll back, eliminating the risk of lost events.
 * Consumed by search-service to keep the Elasticsearch user index current.
 */
@Component
public class UserEventPublisher {

    private final UserOutboxWriter outboxWriter;

    public UserEventPublisher(UserOutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    /**
     * Persists the event to the outbox within the current transaction.
     * Must be called from a {@code @Transactional} method.
     */
    public void publish(UserEvent event) {
        outboxWriter.write(event);
    }

    /** Convenience factory — publishes a CREATED event. */
    public void publishCreated(UUID userId, String name, String email, String username, boolean active) {
        publish(UserEvent.created(userId, name, email, username, active));
    }

    /** Convenience factory — publishes an UPDATED event. */
    public void publishUpdated(UUID userId, String name, String email, String username, boolean active) {
        publish(UserEvent.updated(userId, name, email, username, active));
    }

    /** Convenience factory — publishes a DELETED event. */
    public void publishDeleted(UUID userId) {
        publish(UserEvent.deleted(userId));
    }
}

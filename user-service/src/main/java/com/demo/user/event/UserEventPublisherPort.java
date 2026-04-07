package com.demo.user.event;

import com.demo.common.dto.UserDto;

import java.util.UUID;

/**
 * Port for publishing user lifecycle events. Implemented by {@link KafkaUserEventPublisher}.
 * Extracted as an interface so it can be substituted with a no-op in tests.
 */
public interface UserEventPublisherPort {

    /** Publishes a CREATED event. */
    void publishCreated(UserDto user);

    /** Publishes an UPDATED event. */
    void publishUpdated(UserDto user);

    /** Publishes a DELETED event. */
    void publishDeleted(UUID userId);
}

package com.demo.user.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Repository for user outbox events; used by the scheduled publisher to find pending rows. */
public interface UserOutboxRepository extends JpaRepository<UserOutboxEvent, UUID> {

    /** Returns all events not yet forwarded to Kafka. */
    List<UserOutboxEvent> findByPublishedFalse();
}

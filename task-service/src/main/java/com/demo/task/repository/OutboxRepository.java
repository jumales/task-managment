package com.demo.task.repository;

import com.demo.task.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /** Returns all outbox events that have not yet been published to Kafka. */
    List<OutboxEvent> findByPublishedFalse();
}

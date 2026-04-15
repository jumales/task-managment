package com.demo.task.repository;

import com.demo.task.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Returns up to 100 unpublished outbox events using a pessimistic lock with SKIP LOCKED.
     * With multiple task-service instances, SKIP LOCKED ensures each instance processes a
     * disjoint set of rows — no double-publishing. The enclosing transaction must be active
     * for the FOR UPDATE lock to be held until commit.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE published = false ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 500",
            nativeQuery = true)
    List<OutboxEvent> findUnpublishedForUpdate();

    /**
     * Returns all unpublished outbox events without a row lock.
     * Safe to call outside a transaction (no SKIP LOCKED side effects).
     * Use this in test assertions — {@link #findUnpublishedForUpdate()} uses SKIP LOCKED
     * which can silently skip rows locked by the scheduler and produce false-positive empty results.
     */
    List<OutboxEvent> findByPublishedFalse();
}

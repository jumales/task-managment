package com.demo.notification.repository;

import com.demo.notification.model.NotificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {

    /** Returns a paginated page of notifications for a given task, ordered by send time ascending. */
    Page<NotificationRecord> findByTaskIdOrderBySentAtAsc(UUID taskId, Pageable pageable);

    /**
     * Deletes notification records older than the given cutoff.
     * Called nightly by the TTL cleanup scheduler to keep the table from growing unbounded.
     */
    @Modifying
    @Query(value = "DELETE FROM notifications WHERE sent_at < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

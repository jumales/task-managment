package com.demo.notification.repository;

import com.demo.notification.model.NotificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {

    /** Returns a paginated page of notifications for a given task, ordered by send time ascending. */
    Page<NotificationRecord> findByTaskIdOrderBySentAtAsc(UUID taskId, Pageable pageable);
}

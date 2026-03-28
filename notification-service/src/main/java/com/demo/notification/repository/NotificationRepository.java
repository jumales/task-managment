package com.demo.notification.repository;

import com.demo.notification.model.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {

    /** Returns all notifications for a given task ordered by send time ascending. */
    List<NotificationRecord> findByTaskIdOrderBySentAtAsc(UUID taskId);
}

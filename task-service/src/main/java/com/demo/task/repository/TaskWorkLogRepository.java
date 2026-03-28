package com.demo.task.repository;

import com.demo.task.model.TaskWorkLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskWorkLogRepository extends JpaRepository<TaskWorkLog, UUID> {

    /** Returns all active work log entries for a single task, ordered by creation time. */
    List<TaskWorkLog> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    /** Returns true if any active work log entries exist for the given task. */
    boolean existsByTaskId(UUID taskId);
}

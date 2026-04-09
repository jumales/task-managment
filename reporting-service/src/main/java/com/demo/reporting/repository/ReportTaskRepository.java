package com.demo.reporting.repository;

import com.demo.reporting.model.ReportTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Spring Data access for the reporting read-model of tasks. */
public interface ReportTaskRepository extends JpaRepository<ReportTask, UUID> {

    /** Tasks assigned to the given user, newest first. */
    List<ReportTask> findByAssignedUserIdOrderByUpdatedAtDesc(UUID assignedUserId);

    /** Tasks assigned to the given user and updated on or after the given cutoff, newest first. */
    List<ReportTask> findByAssignedUserIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDesc(
            UUID assignedUserId, Instant updatedAtCutoff);
}

package com.demo.reporting.repository;

import com.demo.reporting.model.ReportTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Spring Data access for the reporting read-model of tasks. */
public interface ReportTaskRepository extends JpaRepository<ReportTask, UUID> {

    /** Open tasks (phase not in the excluded set) assigned to the given user, newest first. */
    List<ReportTask> findByAssignedUserIdAndPhaseNameNotInOrderByUpdatedAtDesc(
            UUID assignedUserId, Collection<String> excludedPhaseNames);

    /** Open tasks updated on or after the given cutoff, phase not in the excluded set, newest first. */
    List<ReportTask> findByAssignedUserIdAndUpdatedAtGreaterThanEqualAndPhaseNameNotInOrderByUpdatedAtDesc(
            UUID assignedUserId, Instant updatedAtCutoff, Collection<String> excludedPhaseNames);

    /** Finished tasks (phase in the given set) assigned to the given user, newest first. */
    List<ReportTask> findByAssignedUserIdAndPhaseNameInOrderByUpdatedAtDesc(
            UUID assignedUserId, Collection<String> phaseNames);

    /** Finished tasks updated on or after the given cutoff, newest first. */
    List<ReportTask> findByAssignedUserIdAndUpdatedAtGreaterThanEqualAndPhaseNameInOrderByUpdatedAtDesc(
            UUID assignedUserId, Instant updatedAtCutoff, Collection<String> phaseNames);
}

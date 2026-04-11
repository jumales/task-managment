package com.demo.reporting.repository;

import com.demo.reporting.model.ReportTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Spring Data access for the reporting read-model of tasks. */
public interface ReportTaskRepository extends JpaRepository<ReportTask, UUID> {

    /**
     * Open tasks assigned to the given user, newest first.
     * Explicit JPQL treats NULL phaseName as open — SQL's NOT IN cannot match NULL.
     */
    @Query("SELECT t FROM ReportTask t WHERE t.assignedUserId = :userId " +
           "AND (t.phaseName IS NULL OR t.phaseName NOT IN :excludedPhaseNames) " +
           "ORDER BY t.updatedAt DESC")
    List<ReportTask> findOpenByAssignedUserIdOrderByUpdatedAtDesc(
            @Param("userId") UUID userId,
            @Param("excludedPhaseNames") Collection<String> excludedPhaseNames);

    /**
     * Open tasks updated on or after the given cutoff, newest first.
     * Treats NULL phaseName as open.
     */
    @Query("SELECT t FROM ReportTask t WHERE t.assignedUserId = :userId " +
           "AND t.updatedAt >= :cutoff " +
           "AND (t.phaseName IS NULL OR t.phaseName NOT IN :excludedPhaseNames) " +
           "ORDER BY t.updatedAt DESC")
    List<ReportTask> findOpenByAssignedUserIdAndUpdatedAtAfterOrderByUpdatedAtDesc(
            @Param("userId") UUID userId,
            @Param("cutoff") Instant cutoff,
            @Param("excludedPhaseNames") Collection<String> excludedPhaseNames);

    /** Finished tasks (phase in the given set) assigned to the given user, newest first. */
    List<ReportTask> findByAssignedUserIdAndPhaseNameInOrderByUpdatedAtDesc(
            UUID assignedUserId, Collection<String> phaseNames);

    /** Finished tasks updated on or after the given cutoff, newest first. */
    List<ReportTask> findByAssignedUserIdAndUpdatedAtGreaterThanEqualAndPhaseNameInOrderByUpdatedAtDesc(
            UUID assignedUserId, Instant updatedAtCutoff, Collection<String> phaseNames);
}

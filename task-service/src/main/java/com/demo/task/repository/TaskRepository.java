package com.demo.task.repository;

import com.demo.common.dto.TaskStatus;
import com.demo.task.model.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    /** Returns a paginated page of tasks assigned to the given user. */
    Page<Task> findByAssignedUserId(UUID userId, Pageable pageable);
    /** Returns a paginated page of tasks assigned to the given user, excluding those in the given phases. */
    Page<Task> findByAssignedUserIdAndPhaseIdNotIn(UUID userId, Collection<UUID> phaseIds, Pageable pageable);
    /** Returns a paginated page of tasks with the given status. */
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    /** Returns a paginated page of tasks with the given status, excluding those in the given phases. */
    Page<Task> findByStatusAndPhaseIdNotIn(TaskStatus status, Collection<UUID> phaseIds, Pageable pageable);
    /** Returns a paginated page of tasks belonging to the given project. */
    Page<Task> findByProjectId(UUID projectId, Pageable pageable);
    /** Returns a paginated page of tasks belonging to the given project, excluding those in the given phases. */
    Page<Task> findByProjectIdAndPhaseIdNotIn(UUID projectId, Collection<UUID> phaseIds, Pageable pageable);
    /** Returns a paginated page of tasks whose phaseId is in the given set. */
    Page<Task> findByPhaseIdIn(Collection<UUID> phaseIds, Pageable pageable);
    /** Returns a paginated page of tasks whose phaseId is NOT in the given set. */
    Page<Task> findByPhaseIdNotIn(Collection<UUID> phaseIds, Pageable pageable);
    /** Returns {@code true} if any non-deleted task references the given project. */
    boolean existsByProjectId(UUID projectId);
    /** Returns {@code true} if any non-deleted task references the given phase. */
    boolean existsByPhaseId(UUID phaseId);

    /**
     * Loads a task by ID with a database-level exclusive row lock (SELECT … FOR UPDATE).
     * Used by the update path to serialize concurrent writes: after the previous holder commits,
     * the next waiter re-reads the committed version, so the manual version check in
     * {@link com.demo.task.service.TaskService#update} correctly rejects stale clients.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") UUID id);

    /** Sets the task code on a single task; used by the async code-assignment scheduler. */
    @Modifying
    @Query("UPDATE Task t SET t.taskCode = :code WHERE t.id = :taskId")
    void updateTaskCode(@Param("taskId") UUID taskId, @Param("code") String code);

    /**
     * Returns tasks in a terminal phase (RELEASED or REJECTED) whose {@code closed_at} is older
     * than the given cutoff, up to {@code limit} rows. Used by the archive scheduler.
     *
     * <p>Native query is required because the Task entity has no JPA relationship to TaskPhase —
     * the join must be expressed in SQL. The {@code deleted_at IS NULL} guard is explicit here
     * because native queries bypass {@code @SQLRestriction}.
     */
    @Query(value = """
            SELECT t.* FROM tasks t
            JOIN task_phases tp ON t.phase_id = tp.id
            WHERE tp.name IN ('RELEASED', 'REJECTED')
              AND t.closed_at < :cutoff
              AND t.deleted_at IS NULL
              AND tp.deleted_at IS NULL
            LIMIT :limit
            """, nativeQuery = true)
    List<Task> findExpiredClosedTasks(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}

package com.demo.task.repository;

import com.demo.common.dto.TimelineState;
import com.demo.task.model.TaskTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskTimelineRepository extends JpaRepository<TaskTimeline, UUID> {

    /** Returns all active timeline entries for the given task, ordered by state. */
    List<TaskTimeline> findByTaskIdOrderByStateAsc(UUID taskId);

    /** Returns the active timeline entry for a specific task and state, if one exists. */
    Optional<TaskTimeline> findByTaskIdAndState(UUID taskId, TimelineState state);

    /** Returns true if any active timeline entry exists for the given task and state. */
    boolean existsByTaskIdAndState(UUID taskId, TimelineState state);

    /**
     * Updates PLANNED_START and PLANNED_END timestamps for a task in a single SQL statement.
     * Uses a CASE expression so both states are written atomically without separate selects.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE task_timelines
               SET timestamp      = CASE state
                                      WHEN 'PLANNED_START' THEN CAST(:plannedStart AS timestamptz)
                                      ELSE                      CAST(:plannedEnd   AS timestamptz)
                                    END,
                   set_by_user_id = :setByUserId
             WHERE task_id   = :taskId
               AND state    IN ('PLANNED_START', 'PLANNED_END')
               AND deleted_at IS NULL
            """)
    void updatePlannedTimestamps(@Param("taskId") UUID taskId,
                                 @Param("plannedStart") Instant plannedStart,
                                 @Param("plannedEnd") Instant plannedEnd,
                                 @Param("setByUserId") UUID setByUserId);
}

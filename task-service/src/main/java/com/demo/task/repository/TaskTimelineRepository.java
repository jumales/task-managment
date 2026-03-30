package com.demo.task.repository;

import com.demo.common.dto.TimelineState;
import com.demo.task.model.TaskTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

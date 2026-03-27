package com.demo.task.repository;

import com.demo.common.dto.TaskParticipantRole;
import com.demo.task.model.TaskParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TaskParticipantRepository extends JpaRepository<TaskParticipant, UUID> {

    /** Returns all active participants for a single task. */
    List<TaskParticipant> findByTaskId(UUID taskId);

    /** Batch-loads all active participants for a set of task IDs. */
    List<TaskParticipant> findByTaskIdIn(Set<UUID> taskIds);

    /** Returns true if the user already holds the given role on the task. */
    boolean existsByTaskIdAndUserIdAndRole(UUID taskId, UUID userId, TaskParticipantRole role);
}

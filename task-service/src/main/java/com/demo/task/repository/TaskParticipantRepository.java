package com.demo.task.repository;

import com.demo.common.dto.TaskParticipantRole;
import com.demo.task.model.TaskParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TaskParticipantRepository extends JpaRepository<TaskParticipant, UUID> {

    /** Returns all active participants for a single task. */
    List<TaskParticipant> findByTaskId(UUID taskId);

    /** Batch-loads all active participants for a set of task IDs. */
    List<TaskParticipant> findByTaskIdIn(Set<UUID> taskIds);

    /** Returns true if the user has any active participant entry on the task (any role). */
    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);

    /** Deletes all participants with the given role on the given task in one query. */
    @Modifying
    @Query("DELETE FROM TaskParticipant p WHERE p.taskId = :taskId AND p.role = :role")
    void deleteByTaskIdAndRole(@Param("taskId") UUID taskId, @Param("role") TaskParticipantRole role);
}

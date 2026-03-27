package com.demo.task.repository;

import com.demo.common.dto.TaskStatus;
import com.demo.task.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    /** Returns all tasks assigned to the given user. */
    List<Task> findByAssignedUserId(UUID userId);
    /** Returns a page of tasks assigned to the given user. */
    Page<Task> findByAssignedUserId(UUID userId, Pageable pageable);
    /** Returns all tasks with the given status. */
    List<Task> findByStatus(TaskStatus status);
    /** Returns a page of tasks with the given status. */
    Page<Task> findByStatus(TaskStatus status, Pageable pageable);
    /** Returns all tasks belonging to the given project. */
    List<Task> findByProjectId(UUID projectId);
    /** Returns a page of tasks belonging to the given project. */
    Page<Task> findByProjectId(UUID projectId, Pageable pageable);
    /** Returns {@code true} if any non-deleted task references the given project. */
    boolean existsByProjectId(UUID projectId);
    /** Returns {@code true} if any non-deleted task references the given phase. */
    boolean existsByPhaseId(UUID phaseId);
}

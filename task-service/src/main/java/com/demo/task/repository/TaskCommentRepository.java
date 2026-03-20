package com.demo.task.repository;

import com.demo.task.model.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    /** Returns all non-deleted comments for a task ordered by creation time ascending. */
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
    /** Returns {@code true} if the task has at least one non-deleted comment. */
    boolean existsByTaskId(UUID taskId);

    /** Soft-deletes all comments associated with the given task. */
    @Modifying
    @Query("UPDATE TaskComment tc SET tc.deletedAt = CURRENT_TIMESTAMP WHERE tc.taskId = :taskId AND tc.deletedAt IS NULL")
    void softDeleteByTaskId(@Param("taskId") UUID taskId);
}

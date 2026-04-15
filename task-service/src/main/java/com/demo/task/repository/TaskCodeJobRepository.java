package com.demo.task.repository;

import com.demo.task.model.TaskCodeJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskCodeJobRepository extends JpaRepository<TaskCodeJob, UUID> {

    /**
     * Returns all pending jobs (processed_at IS NULL) ordered by creation time, with a
     * pessimistic write lock using SKIP LOCKED.
     * With multiple task-service instances, SKIP LOCKED ensures each instance processes
     * a disjoint set — no two instances will assign a code to the same task.
     * The enclosing transaction must be active for the lock to be held until commit.
     */
    @Query(value = "SELECT * FROM task_code_jobs WHERE processed_at IS NULL ORDER BY created_at ASC FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<TaskCodeJob> findPendingForUpdate();
}

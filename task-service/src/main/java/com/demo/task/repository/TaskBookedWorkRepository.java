package com.demo.task.repository;

import com.demo.task.model.TaskBookedWork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskBookedWorkRepository extends JpaRepository<TaskBookedWork, UUID> {

    /** Returns all active booked-work entries for the given task, ordered by creation time. */
    List<TaskBookedWork> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    //TODO: remove not used method
    /** Returns true if any active booked-work entries exist for the given task. */
    boolean existsByTaskId(UUID taskId);
}

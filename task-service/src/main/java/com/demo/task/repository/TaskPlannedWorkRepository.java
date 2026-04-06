package com.demo.task.repository;

import com.demo.common.dto.WorkType;
import com.demo.task.model.TaskPlannedWork;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskPlannedWorkRepository extends JpaRepository<TaskPlannedWork, UUID> {

    /** Returns all planned-work entries for the given task, ordered by creation time. */
    List<TaskPlannedWork> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    /** Returns true if a planned-work entry already exists for the given task and work type. */
    boolean existsByTaskIdAndWorkType(UUID taskId, WorkType workType);

}

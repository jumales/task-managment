package com.demo.task.repository;

import com.demo.common.dto.TaskPhaseName;
import com.demo.task.model.TaskPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskPhaseRepository extends JpaRepository<TaskPhase, UUID> {

    /** Returns all non-deleted phases for the given project. */
    List<TaskPhase> findByProjectId(UUID projectId);

    /** Returns one non-deleted phase with the given name for the given project, if any exist. */
    Optional<TaskPhase> findFirstByProjectIdAndName(UUID projectId, TaskPhaseName name);

    /** Returns all non-deleted phases whose name is in the given set (across all projects). */
    List<TaskPhase> findByNameIn(Collection<TaskPhaseName> names);
}

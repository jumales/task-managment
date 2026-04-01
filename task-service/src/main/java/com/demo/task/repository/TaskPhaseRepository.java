package com.demo.task.repository;

import com.demo.task.model.TaskPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskPhaseRepository extends JpaRepository<TaskPhase, UUID> {

    /** Returns all non-deleted phases for the given project. */
    List<TaskPhase> findByProjectId(UUID projectId);
}

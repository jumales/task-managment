package com.demo.task.repository;

import com.demo.task.model.TaskPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskPhaseRepository extends JpaRepository<TaskPhase, UUID> {

    /** Returns all non-deleted phases for the given project. */
    List<TaskPhase> findByProjectId(UUID projectId);

    /** Returns the default phase for the given project, if one exists. */
    Optional<TaskPhase> findByProjectIdAndIsDefaultTrue(UUID projectId);

    /** Clears the default flag on any current default phase for the project before setting a new one. */
    @Modifying
    @Query("UPDATE TaskPhase tp SET tp.isDefault = false WHERE tp.projectId = :projectId AND tp.isDefault = true AND tp.deletedAt IS NULL")
    void clearDefaultForProject(@Param("projectId") UUID projectId);
}

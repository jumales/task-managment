package com.demo.task.repository;

import com.demo.task.model.TaskProject;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TaskProjectRepository extends JpaRepository<TaskProject, UUID> {

    /** Fetches the project with a pessimistic write lock for atomic counter increments. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TaskProject p WHERE p.id = :id")
    Optional<TaskProject> findByIdForUpdate(@Param("id") UUID id);
}

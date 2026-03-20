package com.demo.task.repository;

import com.demo.task.model.TaskProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskProjectRepository extends JpaRepository<TaskProject, UUID> {
}

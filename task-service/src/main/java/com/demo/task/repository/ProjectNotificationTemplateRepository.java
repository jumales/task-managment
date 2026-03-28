package com.demo.task.repository;

import com.demo.common.event.TaskChangeType;
import com.demo.task.model.ProjectNotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for per-project notification email templates. */
public interface ProjectNotificationTemplateRepository extends JpaRepository<ProjectNotificationTemplate, UUID> {

    /** Returns all active templates configured for the given project. */
    List<ProjectNotificationTemplate> findByProjectId(UUID projectId);

    /** Returns the active template for the given project and event type, if any. */
    Optional<ProjectNotificationTemplate> findByProjectIdAndEventType(UUID projectId, TaskChangeType eventType);
}

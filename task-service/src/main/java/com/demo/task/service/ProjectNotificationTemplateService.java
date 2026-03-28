package com.demo.task.service;

import com.demo.common.dto.ProjectNotificationTemplateRequest;
import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.event.TaskChangeType;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.model.ProjectNotificationTemplate;
import com.demo.task.repository.ProjectNotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages per-project email notification templates.
 * Each project may define one template per {@link TaskChangeType}.
 * When a template is configured, the notification-service uses it in place of the default email content.
 */
@Service
public class ProjectNotificationTemplateService {

    private final ProjectNotificationTemplateRepository repository;
    private final TaskProjectService projectService;

    public ProjectNotificationTemplateService(ProjectNotificationTemplateRepository repository,
                                              TaskProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    /** Returns all active templates configured for the given project. */
    public List<ProjectNotificationTemplateResponse> findByProjectId(UUID projectId) {
        projectService.getOrThrow(projectId);
        return repository.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the active template for the given project and event type.
     *
     * @throws ResourceNotFoundException if no template is configured for that combination
     */
    public ProjectNotificationTemplateResponse findByProjectIdAndEventType(UUID projectId,
                                                                            TaskChangeType eventType) {
        return toResponse(getOrThrow(projectId, eventType));
    }

    /**
     * Creates or replaces the template for a project + event type combination.
     * If a template already exists it is soft-deleted and a new one is inserted.
     */
    @Transactional
    public ProjectNotificationTemplateResponse upsert(UUID projectId, TaskChangeType eventType,
                                                       ProjectNotificationTemplateRequest request) {
        projectService.getOrThrow(projectId);
        // Soft-delete any existing template for the same project + event type
        repository.findByProjectIdAndEventType(projectId, eventType)
                .ifPresent(existing -> repository.deleteById(existing.getId()));

        ProjectNotificationTemplate saved = repository.save(ProjectNotificationTemplate.builder()
                .projectId(projectId)
                .eventType(eventType)
                .subjectTemplate(request.getSubjectTemplate())
                .bodyTemplate(request.getBodyTemplate())
                .build());
        return toResponse(saved);
    }

    /**
     * Soft-deletes the template for the given project and event type.
     *
     * @throws ResourceNotFoundException if no template is configured for that combination
     */
    @Transactional
    public void delete(UUID projectId, TaskChangeType eventType) {
        ProjectNotificationTemplate template = getOrThrow(projectId, eventType);
        repository.deleteById(template.getId());
    }

    /** Returns the raw template entity or throws if not found. */
    Optional<ProjectNotificationTemplate> findRaw(UUID projectId, TaskChangeType eventType) {
        return repository.findByProjectIdAndEventType(projectId, eventType);
    }

    private ProjectNotificationTemplate getOrThrow(UUID projectId, TaskChangeType eventType) {
        return repository.findByProjectIdAndEventType(projectId, eventType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProjectNotificationTemplate", projectId + "/" + eventType));
    }

    private ProjectNotificationTemplateResponse toResponse(ProjectNotificationTemplate t) {
        return new ProjectNotificationTemplateResponse(
                t.getId(), t.getProjectId(), t.getEventType(),
                t.getSubjectTemplate(), t.getBodyTemplate());
    }
}

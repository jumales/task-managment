package com.demo.task.service;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.model.TaskPhase;
import com.demo.task.model.TaskProject;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TaskProjectService {

    static final String DEFAULT_TASK_CODE_PREFIX = "TASK_";

    private final TaskProjectRepository repository;
    private final TaskRepository taskRepository;
    private final TaskPhaseService phaseService;

    public TaskProjectService(TaskProjectRepository repository,
                              TaskRepository taskRepository,
                              TaskPhaseService phaseService) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.phaseService = phaseService;
    }

    /** Returns a page of projects sorted and limited by the given {@link Pageable}. */
    public Page<TaskProjectResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    /** Returns the project with the given ID, or throws {@link ResourceNotFoundException}. */
    public TaskProjectResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /**
     * Creates and persists a new project from the given request, then asynchronously creates one phase
     * for every {@link com.demo.common.dto.TaskPhaseName} value so the project starts with a complete
     * phase set. Uses "TASK_" prefix when none is provided.
     */
    public TaskProjectResponse create(TaskProjectRequest request) {
        String prefix = resolvePrefix(request.getTaskCodePrefix());
        TaskProject project = TaskProject.builder()
                .name(request.getName())
                .description(request.getDescription())
                .taskCodePrefix(prefix)
                .nextTaskNumber(1)
                .build();
        TaskProject saved = repository.save(project);
        //TODO do async creation of default phases
        phaseService.createDefaultPhasesForProject(saved.getId());
        return toResponse(saved);
    }

    /** Updates name, description, task code prefix, and default phase of the project identified by {@code id}. */
    public TaskProjectResponse update(UUID id, TaskProjectRequest request) {
        TaskProject project = getOrThrow(id);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        if (request.getTaskCodePrefix() != null) {
            project.setTaskCodePrefix(resolvePrefix(request.getTaskCodePrefix()));
        }
        project.setDefaultPhaseId(resolveDefaultPhaseId(request.getDefaultPhaseId(), id));
        return toResponse(repository.save(project));
    }

    /**
     * Atomically reserves the next sequential task code for the given project.
     * Acquires a pessimistic write lock, increments the counter, and returns the new code
     * (e.g. "PROJ_5"). Must be called within an active transaction.
     *
     * <p><strong>Call ordering requirement:</strong> this method must be called
     * <em>before</em> any plain {@code findById} / {@code getOrThrow} call for the same
     * project within the enclosing transaction. Hibernate's L1 session cache intercepts
     * the {@code SELECT … FOR UPDATE} query and returns the already-loaded entity without
     * hitting the database, which skips the row lock. Calling this first guarantees the
     * entity is not yet in the cache so the lock SQL always executes.
     */
    @Transactional
    String nextTaskCode(UUID projectId) {
        TaskProject project = repository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskProject", projectId));
        int number = project.getNextTaskNumber();
        project.setNextTaskNumber(number + 1);
        repository.save(project);
        return project.getTaskCodePrefix() + number;
    }

    /** Soft-deletes the project; throws if any tasks still belong to it. */
    public void delete(UUID id) {
        getOrThrow(id);
        if (taskRepository.existsByProjectId(id)) {
            throw new RelatedEntityActiveException("TaskProject", "tasks");
        }
        repository.deleteById(id);
    }

    /** Returns raw entities for all given IDs; missing IDs are silently skipped. Package-private for batch loading in TaskService list responses. */
    List<TaskProject> findAllByIds(Iterable<UUID> ids) {
        return repository.findAllById(ids);
    }

    /** Returns the raw {@link TaskProject} entity, or throws {@link ResourceNotFoundException}. Package-private for use by {@link TaskService}. */
    TaskProject getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaskProject", id));
    }

    /**
     * Validates that the given phase ID belongs to the given project, then returns it.
     * Returns null when {@code phaseId} is null (no default configured).
     */
    private UUID resolveDefaultPhaseId(UUID phaseId, UUID projectId) {
        if (phaseId == null) {
            return null;
        }
        TaskPhase phase = phaseService.getOrThrow(phaseId);
        if (!phase.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Default phase does not belong to this project");
        }
        return phaseId;
    }

    /** Returns "TASK_" when the given prefix is blank or null. */
    private String resolvePrefix(String prefix) {
        return (prefix != null && !prefix.isBlank()) ? prefix : DEFAULT_TASK_CODE_PREFIX;
    }

    /** Converts a {@link TaskProject} entity to its DTO representation. */
    private TaskProjectResponse toResponse(TaskProject project) {
        return new TaskProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getTaskCodePrefix(), project.getDefaultPhaseId());
    }
}

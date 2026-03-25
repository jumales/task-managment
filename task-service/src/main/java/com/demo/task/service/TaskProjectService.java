package com.demo.task.service;

import com.demo.common.dto.TaskProjectRequest;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.model.TaskProject;
import com.demo.task.repository.TaskProjectRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TaskProjectService {

    private final TaskProjectRepository repository;
    private final TaskRepository taskRepository;

    public TaskProjectService(TaskProjectRepository repository, TaskRepository taskRepository) {
        this.repository = repository;
        this.taskRepository = taskRepository;
    }

    /** Returns all projects. */
    public List<TaskProjectResponse> findAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    /** Returns the project with the given ID, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public TaskProjectResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /** Creates and persists a new project from the given request. */
    public TaskProjectResponse create(TaskProjectRequest request) {
        TaskProject project = TaskProject.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toResponse(repository.save(project));
    }

    /** Updates name and description of the project identified by {@code id}. */
    public TaskProjectResponse update(UUID id, TaskProjectRequest request) {
        TaskProject project = getOrThrow(id);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        return toResponse(repository.save(project));
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

    /** Returns the raw {@link com.demo.task.model.TaskProject} entity, or throws {@link com.demo.common.exception.ResourceNotFoundException}. Package-private for use by {@link com.demo.task.service.TaskService}. */
    TaskProject getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaskProject", id));
    }

    /** Converts a {@link com.demo.task.model.TaskProject} entity to its DTO representation. */
    TaskProjectResponse toResponse(TaskProject project) {
        return new TaskProjectResponse(project.getId(), project.getName(), project.getDescription());
    }
}

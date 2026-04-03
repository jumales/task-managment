package com.demo.task.service;

import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.model.TaskPhase;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TaskPhaseService {

    private final TaskPhaseRepository phaseRepository;
    private final TaskRepository taskRepository;

    public TaskPhaseService(TaskPhaseRepository phaseRepository, TaskRepository taskRepository) {
        this.phaseRepository = phaseRepository;
        this.taskRepository = taskRepository;
    }

    /** Returns all phases belonging to the specified project. */
    public List<TaskPhaseResponse> findByProject(UUID projectId) {
        return phaseRepository.findByProjectId(projectId).stream().map(this::toResponse).toList();
    }

    /** Returns the phase with the given ID, or throws {@link ResourceNotFoundException}. */
    public TaskPhaseResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /** Creates a new phase for the project specified in the request body. */
    public TaskPhaseResponse create(TaskPhaseRequest request) {
        TaskPhase phase = TaskPhase.builder()
                .projectId(request.getProjectId())
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toResponse(phaseRepository.save(phase));
    }

    /** Updates the phase identified by {@code id} with values from the request body. */
    public TaskPhaseResponse update(UUID id, TaskPhaseRequest request) {
        TaskPhase phase = getOrThrow(id);
        phase.setName(request.getName());
        phase.setDescription(request.getDescription());
        return toResponse(phaseRepository.save(phase));
    }

    /** Soft-deletes the phase; throws if any tasks are still assigned to it. */
    public void delete(UUID id) {
        getOrThrow(id);
        if (taskRepository.existsByPhaseId(id)) {
            throw new RelatedEntityActiveException("TaskPhase", "tasks");
        }
        phaseRepository.deleteById(id);
    }

    /** Returns raw entities for all given IDs; missing IDs are silently skipped. Package-private for batch loading in TaskService list responses. */
    List<TaskPhase> findAllByIds(Iterable<UUID> ids) {
        return phaseRepository.findAllById(ids);
    }

    /** Returns the raw entity; package-private for use by TaskService and TaskProjectService. */
    TaskPhase getOrThrow(UUID id) {
        return phaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaskPhase", id));
    }

    /** Converts a {@link TaskPhase} entity to its DTO representation. */
    TaskPhaseResponse toResponse(TaskPhase phase) {
        return new TaskPhaseResponse(
                phase.getId(),
                phase.getName(),
                phase.getDescription(),
                phase.getCustomName(),
                phase.getProjectId());
    }
}

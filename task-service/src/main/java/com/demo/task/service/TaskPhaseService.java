package com.demo.task.service;

import com.demo.common.dto.TaskPhaseRequest;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.model.TaskPhase;
import com.demo.task.repository.TaskPhaseRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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

    /** Returns the phase with the given ID, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public TaskPhaseResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /** Creates a new phase; if marked as default, clears any existing default for the same project first. */
    @Transactional
    public TaskPhaseResponse create(TaskPhaseRequest request) {
        if (request.isDefault()) {
            phaseRepository.clearDefaultForProject(request.getProjectId());
        }
        TaskPhase phase = TaskPhase.builder()
                .projectId(request.getProjectId())
                .name(request.getName())
                .description(request.getDescription())
                .isDefault(request.isDefault())
                .build();
        return toResponse(phaseRepository.save(phase));
    }

    /** Updates the phase; if the new request sets it as default, clears the previous default for the project. */
    @Transactional
    public TaskPhaseResponse update(UUID id, TaskPhaseRequest request) {
        TaskPhase phase = getOrThrow(id);
        if (request.isDefault() && !phase.isDefault()) {
            phaseRepository.clearDefaultForProject(phase.getProjectId());
        }
        phase.setName(request.getName());
        phase.setDescription(request.getDescription());
        phase.setDefault(request.isDefault());
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

    /** Returns the default phase for the given project, if one exists. */
    Optional<TaskPhase> findDefaultForProject(UUID projectId) {
        return phaseRepository.findByProjectIdAndIsDefaultTrue(projectId);
    }

    /** Returns the raw entity; package-private for use by TaskService. */
    TaskPhase getOrThrow(UUID id) {
        return phaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaskPhase", id));
    }

    /** Converts a {@link com.demo.task.model.TaskPhase} entity to its DTO representation. */
    TaskPhaseResponse toResponse(TaskPhase phase) {
        return new TaskPhaseResponse(
                phase.getId(),
                phase.getName(),
                phase.getDescription(),
                phase.getProjectId(),
                phase.isDefault());
    }
}

package com.demo.task.service;

import com.demo.common.dto.TaskPlannedWorkRequest;
import com.demo.common.dto.TaskPlannedWorkResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.Task;
import com.demo.task.model.TaskPlannedWork;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.TaskPlannedWorkRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages planned-work entries for tasks, tracking estimated hours per work type.
 * Each work type can have at most one planned-work entry per task.
 * Entries are immutable once created and may only be added while the task status is TODO.
 * Publishes an audit event to the outbox on every create.
 */
@Service
public class TaskPlannedWorkService {

    private static final String ENTITY_NAME = "TaskPlannedWork";

    private final TaskPlannedWorkRepository repository;
    private final TaskRepository taskRepository;
    private final UserClientHelper userClientHelper;
    private final OutboxWriter outboxWriter;

    public TaskPlannedWorkService(TaskPlannedWorkRepository repository,
                                  TaskRepository taskRepository,
                                  UserClientHelper userClientHelper,
                                  OutboxWriter outboxWriter) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.userClientHelper = userClientHelper;
        this.outboxWriter = outboxWriter;
    }

    /** Returns all planned-work entries for the given task, enriched with user display names. */
    public List<TaskPlannedWorkResponse> findByTaskId(UUID taskId) {
        getTaskOrThrow(taskId);
        return toResponseList(repository.findByTaskIdOrderByCreatedAtAsc(taskId));
    }

    /**
     * Creates a new planned-work entry on the task.
     * Only allowed when the task status is TODO (planning phase).
     * Each work type may appear at most once per task.
     * Publishes a PLANNED_WORK_CREATED audit event.
     *
     * @param creatorId the authenticated user who is creating this entry
     * @throws IllegalArgumentException if the task is not in TODO status
     * @throws DuplicateResourceException if a planned-work entry for the given work type already exists
     */
    @Transactional
    public TaskPlannedWorkResponse create(UUID taskId, UUID creatorId, TaskPlannedWorkRequest request) {
        Task task = getTaskOrThrow(taskId);
        validatePlanningPhase(task);
        validateNoDuplicate(taskId, request);
        String creatorName = userClientHelper.resolveUserName(creatorId);
        TaskPlannedWork saved = repository.save(TaskPlannedWork.builder()
                .taskId(taskId)
                .userId(creatorId)
                .workType(request.getWorkType())
                .plannedHours(request.getPlannedHours() != null ? request.getPlannedHours().intValue() : 0)
                .createdAt(Instant.now())
                .build());
        outboxWriter.write(TaskChangedEvent.plannedWorkCreated(taskId, task.getProjectId(), task.getTitle(),
                saved.getId(), saved.getUserId(), saved.getWorkType(),
                BigInteger.valueOf(saved.getPlannedHours())));
        return toResponse(saved, creatorName);
    }

    private Task getTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    /** Throws if the task is not in TODO status (planning phase). */
    private void validatePlanningPhase(Task task) {
        if (task.getStatus() != TaskStatus.TODO) {
            throw new IllegalArgumentException(
                    "Planned work can only be created when task status is TODO (planning phase)");
        }
    }

    /** Throws if a planned-work entry for the requested work type already exists on the task. */
    private void validateNoDuplicate(UUID taskId, TaskPlannedWorkRequest request) {
        if (repository.existsByTaskIdAndWorkType(taskId, request.getWorkType())) {
            throw new DuplicateResourceException(
                    "Planned work for work type " + request.getWorkType() + " already exists on this task");
        }
    }

    /** Enriches a list of planned-work entries with user display names using a single batch call. */
    private List<TaskPlannedWorkResponse> toResponseList(List<TaskPlannedWork> entries) {
        if (entries.isEmpty()) return List.of();
        Set<UUID> userIds = entries.stream().map(TaskPlannedWork::getUserId).collect(Collectors.toSet());
        Map<UUID, String> nameById = userClientHelper.fetchUserNames(userIds);
        return entries.stream()
                .map(e -> toResponse(e, nameById.get(e.getUserId())))
                .toList();
    }

    private TaskPlannedWorkResponse toResponse(TaskPlannedWork entry, String userName) {
        return new TaskPlannedWorkResponse(
                entry.getId(),
                entry.getUserId(),
                userName,
                entry.getWorkType(),
                BigInteger.valueOf(entry.getPlannedHours()),
                entry.getCreatedAt());
    }
}

package com.demo.task.service;

import com.demo.common.dto.TaskBookedWorkRequest;
import com.demo.common.dto.TaskBookedWorkResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.exception.BusinessLogicException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.Task;
import com.demo.task.model.TaskBookedWork;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.TaskBookedWorkRepository;
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
 * Manages booked-work entries for tasks, tracking actual hours worked per user and work type.
 * Multiple entries are allowed per task and work type.
 * Publishes audit events to the outbox for every create, update, and delete operation.
 */
@Service
public class TaskBookedWorkService {

    private static final String ENTITY_NAME = "TaskBookedWork";

    private final TaskBookedWorkRepository repository;
    private final TaskRepository taskRepository;
    private final TaskPhaseService phaseService;
    private final UserClientHelper userClientHelper;
    private final OutboxWriter outboxWriter;
    private final TaskParticipantService participantService;

    public TaskBookedWorkService(TaskBookedWorkRepository repository,
                                 TaskRepository taskRepository,
                                 TaskPhaseService phaseService,
                                 UserClientHelper userClientHelper,
                                 OutboxWriter outboxWriter,
                                 TaskParticipantService participantService) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.phaseService = phaseService;
        this.userClientHelper = userClientHelper;
        this.outboxWriter = outboxWriter;
        this.participantService = participantService;
    }

    /** Returns all active booked-work entries for the given task, enriched with user display names. */
    public List<TaskBookedWorkResponse> findByTaskId(UUID taskId) {
        getTaskOrThrow(taskId);
        return toResponseList(repository.findByTaskIdOrderByCreatedAtAsc(taskId));
    }

    /**
     * Creates a new booked-work entry on the task, attributed to the given userId.
     * Validates that both the task and the referenced user exist.
     * Blocked when the task is in PLANNING phase — use planned work for estimates instead.
     * Blocked when the task is in RELEASED or REJECTED phase — fully finished tasks are immutable.
     * Publishes a BOOKED_WORK_CREATED audit event.
     *
     * <p>Uses {@link UserClientHelper#fetchUser} (Caffeine cache + circuit breaker) instead of
     * calling the Feign client directly, so repeated lookups for the same user are served from
     * memory and the service degrades gracefully when user-service is temporarily unavailable.
     */
    @Transactional
    public TaskBookedWorkResponse create(UUID taskId, UUID userId, TaskBookedWorkRequest request) {
        Task task = getTaskOrThrow(taskId);
        TaskPhaseName phaseName = resolvePhaseName(task);
        validateNotPlanningPhase(phaseName);
        validateNotFinished(phaseName);
        UserDto user = userClientHelper.fetchUser(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User", userId);
        }
        TaskBookedWork saved = repository.save(TaskBookedWork.builder()
                .taskId(taskId)
                .userId(userId)
                .workType(request.getWorkType())
                .bookedHours(request.getBookedHours() != null ? request.getBookedHours().intValue() : 0)
                .createdAt(Instant.now())
                .build());
        outboxWriter.write(TaskChangedEvent.bookedWorkCreated(taskId, task.getProjectId(), task.getTitle(),
                saved.getId(), saved.getUserId(), saved.getWorkType(),
                BigInteger.valueOf(saved.getBookedHours())));
        // Auto-register the user as a CONTRIBUTOR if not already a participant
        participantService.addIfNotPresent(taskId, userId, TaskParticipantRole.CONTRIBUTOR);
        return toResponse(saved, user.getName());
    }

    /**
     * Updates workType and bookedHours of an existing booked-work entry; userId is kept as provided by the caller.
     * Blocked when the task is in PLANNING phase.
     * Blocked when the task is in RELEASED or REJECTED phase — fully finished tasks are immutable.
     * Publishes a BOOKED_WORK_UPDATED audit event.
     */
    @Transactional
    public TaskBookedWorkResponse update(UUID taskId, UUID bookedWorkId, UUID userId, TaskBookedWorkRequest request) {
        Task task = getTaskOrThrow(taskId);
        TaskPhaseName phaseName = resolvePhaseName(task);
        validateNotPlanningPhase(phaseName);
        validateNotFinished(phaseName);
        TaskBookedWork entry = getOrThrow(bookedWorkId);
        UserDto user = userClientHelper.fetchUser(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User", userId);
        }
        entry.setUserId(userId);
        entry.setWorkType(request.getWorkType());
        entry.setBookedHours(request.getBookedHours() != null ? request.getBookedHours().intValue() : 0);
        TaskBookedWork saved = repository.save(entry);
        outboxWriter.write(TaskChangedEvent.bookedWorkUpdated(taskId, task.getProjectId(), task.getTitle(),
                saved.getId(), saved.getUserId(), saved.getWorkType(),
                BigInteger.valueOf(saved.getBookedHours())));
        return toResponse(saved, user.getName());
    }

    /**
     * Soft-deletes a booked-work entry; throws if not found.
     * Blocked when the task is in RELEASED or REJECTED phase — fully finished tasks are immutable.
     * Publishes a BOOKED_WORK_DELETED audit event.
     */
    @Transactional
    public void delete(UUID taskId, UUID bookedWorkId) {
        Task task = getTaskOrThrow(taskId);
        validateNotFinished(resolvePhaseName(task));
        if (!repository.existsById(bookedWorkId)) {
            throw new ResourceNotFoundException(ENTITY_NAME, bookedWorkId);
        }
        repository.deleteById(bookedWorkId);
        outboxWriter.write(TaskChangedEvent.bookedWorkDeleted(taskId, task.getProjectId(), task.getTitle(),
                bookedWorkId));
    }

    /** Returns the current phase name for the task; single lookup shared across guards. */
    private TaskPhaseName resolvePhaseName(Task task) {
        return phaseService.getOrThrow(task.getPhaseId()).getName();
    }

    /** Throws if the task is currently in the PLANNING phase; booked work requires an active phase. */
    private void validateNotPlanningPhase(TaskPhaseName phaseName) {
        if (phaseName == TaskPhaseName.PLANNING) {
            throw new BusinessLogicException(
                    "Booked work cannot be entered while the task is in the PLANNING phase");
        }
    }

    /** Throws if the task is in RELEASED or REJECTED phase — fully finished tasks are immutable. */
    private void validateNotFinished(TaskPhaseName phaseName) {
        if (TaskPhaseName.FINISHED_PHASES.contains(phaseName)) {
            throw new BusinessLogicException("Task is finished and cannot be modified");
        }
    }

    private Task getTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private TaskBookedWork getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, id));
    }

    /** Enriches a list of booked-work entries with user display names using a single batch call. */
    private List<TaskBookedWorkResponse> toResponseList(List<TaskBookedWork> entries) {
        if (entries.isEmpty()) return List.of();
        Set<UUID> userIds = entries.stream().map(TaskBookedWork::getUserId).collect(Collectors.toSet());
        Map<UUID, String> nameById = userClientHelper.fetchUserNames(userIds);
        return entries.stream()
                .map(e -> toResponse(e, nameById.get(e.getUserId())))
                .toList();
    }

    private TaskBookedWorkResponse toResponse(TaskBookedWork entry, String userName) {
        return new TaskBookedWorkResponse(
                entry.getId(),
                entry.getUserId(),
                userName,
                entry.getWorkType(),
                BigInteger.valueOf(entry.getBookedHours()),
                entry.getCreatedAt());
    }
}

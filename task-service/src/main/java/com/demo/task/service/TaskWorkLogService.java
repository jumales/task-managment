package com.demo.task.service;

import java.math.BigInteger;
import com.demo.common.dto.TaskWorkLogRequest;
import com.demo.common.dto.TaskWorkLogResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.TaskWorkLog;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.model.Task;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskWorkLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages work log entries for tasks, tracking planned and booked hours per user and work type.
 * Publishes audit events to the outbox for every create, update, and delete operation.
 */
@Service
public class TaskWorkLogService {

    private final TaskWorkLogRepository repository;
    private final TaskRepository taskRepository;
    private final UserClient userClient;
    private final UserClientHelper userClientHelper;
    private final OutboxWriter outboxWriter;

    public TaskWorkLogService(TaskWorkLogRepository repository,
                              TaskRepository taskRepository,
                              UserClient userClient,
                              UserClientHelper userClientHelper,
                              OutboxWriter outboxWriter) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.userClient = userClient;
        this.userClientHelper = userClientHelper;
        this.outboxWriter = outboxWriter;
    }

    /** Returns all work log entries for the given task, enriched with user display names. */
    public List<TaskWorkLogResponse> findByTaskId(UUID taskId) {
        getTaskOrThrow(taskId);
        List<TaskWorkLog> logs = repository.findByTaskIdOrderByCreatedAtAsc(taskId);
        return toResponseList(logs);
    }

    /**
     * Creates a new work log entry on the task.
     * Validates that both the task and the user exist before persisting.
     * Publishes a WORK_LOG_CREATED audit event.
     */
    @Transactional
    public TaskWorkLogResponse create(UUID taskId, TaskWorkLogRequest request) {
        Task task = getTaskOrThrow(taskId);
        UserDto user = userClient.getUserById(request.getUserId());
        TaskWorkLog saved = repository.save(TaskWorkLog.builder()
                .taskId(taskId)
                .userId(request.getUserId())
                .workType(request.getWorkType())
                .plannedHours(request.getPlannedHours() != null ? request.getPlannedHours().intValue() : 0)
                .bookedHours(request.getBookedHours()  != null ? request.getBookedHours().intValue()  : 0)
                .createdAt(Instant.now())
                .build());
        outboxWriter.write(TaskChangedEvent.workLogCreated(taskId, task.getProjectId(), task.getTitle(),
                saved.getId(), saved.getUserId(), saved.getWorkType(),
                BigInteger.valueOf(saved.getPlannedHours()), BigInteger.valueOf(saved.getBookedHours())));
        return toResponse(saved, user.getName());
    }

    /**
     * Updates the userId, workType, and bookedHours of an existing work log entry.
     * plannedHours is immutable once set and is intentionally not updated here.
     * Publishes a WORK_LOG_UPDATED audit event.
     */
    @Transactional
    public TaskWorkLogResponse update(UUID taskId, UUID workLogId, TaskWorkLogRequest request) {
        Task task = getTaskOrThrow(taskId);
        TaskWorkLog log = getOrThrow(workLogId);
        UserDto user = userClient.getUserById(request.getUserId());
        log.setUserId(request.getUserId());
        log.setWorkType(request.getWorkType());
        log.setBookedHours(request.getBookedHours() != null ? request.getBookedHours().intValue() : 0);
        // plannedHours is immutable: intentionally not updated
        TaskWorkLog saved = repository.save(log);
        outboxWriter.write(TaskChangedEvent.workLogUpdated(taskId, task.getProjectId(), task.getTitle(),
                saved.getId(), saved.getUserId(), saved.getWorkType(),
                BigInteger.valueOf(saved.getPlannedHours()), BigInteger.valueOf(saved.getBookedHours())));
        return toResponse(saved, user.getName());
    }

    /**
     * Soft-deletes a work log entry; throws if not found.
     * Publishes a WORK_LOG_DELETED audit event.
     */
    @Transactional
    public void delete(UUID taskId, UUID workLogId) {
        Task task = getTaskOrThrow(taskId);
        if (!repository.existsById(workLogId)) {
            throw new ResourceNotFoundException("TaskWorkLog", workLogId);
        }
        repository.deleteById(workLogId);
        outboxWriter.write(TaskChangedEvent.workLogDeleted(taskId, task.getProjectId(), task.getTitle(), workLogId));
    }

    private Task getTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private TaskWorkLog getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkLog", id));
    }

    /** Enriches a list of work logs with user display names using a single batch call. */
    private List<TaskWorkLogResponse> toResponseList(List<TaskWorkLog> logs) {
        if (logs.isEmpty()) return List.of();
        Set<UUID> userIds = logs.stream().map(TaskWorkLog::getUserId).collect(Collectors.toSet());
        Map<UUID, String> nameById = userClientHelper.fetchUserNames(userIds);
        return logs.stream()
                .map(l -> toResponse(l, nameById.get(l.getUserId())))
                .toList();
    }

    private TaskWorkLogResponse toResponse(TaskWorkLog log, String userName) {
        return new TaskWorkLogResponse(
                log.getId(),
                log.getUserId(),
                userName,
                log.getWorkType(),
                BigInteger.valueOf(log.getPlannedHours()),
                BigInteger.valueOf(log.getBookedHours()),
                log.getCreatedAt());
    }

}

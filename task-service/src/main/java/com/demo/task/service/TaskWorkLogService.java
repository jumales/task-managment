package com.demo.task.service;

import com.demo.common.dto.TaskWorkLogRequest;
import com.demo.common.dto.TaskWorkLogResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.model.TaskWorkLog;
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
 */
@Service
public class TaskWorkLogService {

    private final TaskWorkLogRepository repository;
    private final TaskRepository taskRepository;
    private final UserClient userClient;

    public TaskWorkLogService(TaskWorkLogRepository repository,
                              TaskRepository taskRepository,
                              UserClient userClient) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.userClient = userClient;
    }

    /** Returns all work log entries for the given task, enriched with user display names. */
    public List<TaskWorkLogResponse> findByTaskId(UUID taskId) {
        assertTaskExists(taskId);
        List<TaskWorkLog> logs = repository.findByTaskIdOrderByCreatedAtAsc(taskId);
        return toResponseList(logs);
    }

    /**
     * Creates a new work log entry on the task.
     * Validates that both the task and the user exist before persisting.
     */
    @Transactional
    public TaskWorkLogResponse create(UUID taskId, TaskWorkLogRequest request) {
        assertTaskExists(taskId);
        UserDto user = userClient.getUserById(request.getUserId());
        TaskWorkLog saved = repository.save(TaskWorkLog.builder()
                .taskId(taskId)
                .userId(request.getUserId())
                .workType(request.getWorkType())
                .plannedHours(request.getPlannedHours())
                .bookedHours(request.getBookedHours())
                .createdAt(Instant.now())
                .build());
        return toResponse(saved, user.getName());
    }

    /**
     * Updates the userId, workType, and bookedHours of an existing work log entry.
     * plannedHours is immutable once set and is intentionally not updated here.
     */
    @Transactional
    public TaskWorkLogResponse update(UUID taskId, UUID workLogId, TaskWorkLogRequest request) {
        assertTaskExists(taskId);
        TaskWorkLog log = getOrThrow(workLogId);
        UserDto user = userClient.getUserById(request.getUserId());
        log.setUserId(request.getUserId());
        log.setWorkType(request.getWorkType());
        log.setBookedHours(request.getBookedHours());
        // plannedHours is immutable: intentionally not updated
        return toResponse(repository.save(log), user.getName());
    }

    /** Soft-deletes a work log entry; throws if not found. */
    @Transactional
    public void delete(UUID taskId, UUID workLogId) {
        assertTaskExists(taskId);
        if (!repository.existsById(workLogId)) {
            throw new ResourceNotFoundException("TaskWorkLog", workLogId);
        }
        repository.deleteById(workLogId);
    }

    private void assertTaskExists(UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
    }

    private TaskWorkLog getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkLog", id));
    }

    /** Enriches a list of work logs with user display names using a single batch call. */
    private List<TaskWorkLogResponse> toResponseList(List<TaskWorkLog> logs) {
        if (logs.isEmpty()) return List.of();
        Set<UUID> userIds = logs.stream().map(TaskWorkLog::getUserId).collect(Collectors.toSet());
        Map<UUID, String> nameById = fetchUserNames(userIds);
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
                log.getPlannedHours(),
                log.getBookedHours(),
                log.getCreatedAt());
    }

    /** Batch-fetches user names; returns empty map if user-service is unavailable. */
    private Map<UUID, String> fetchUserNames(Set<UUID> userIds) {
        try {
            return userClient.getUsersByIds(userIds.stream().toList()).stream()
                    .collect(Collectors.toMap(UserDto::getId, UserDto::getName));
        } catch (Exception e) {
            return Map.of();
        }
    }
}

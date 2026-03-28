package com.demo.task.service;

import java.math.BigInteger;
import com.demo.common.dto.TaskWorkLogRequest;
import com.demo.common.dto.TaskWorkLogResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.model.OutboxEvent;
import com.demo.task.model.OutboxEventType;
import com.demo.task.model.TaskWorkLog;
import com.demo.task.outbox.OutboxPublisher;
import com.demo.task.repository.OutboxRepository;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskWorkLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String AGGREGATE_TYPE = "Task";

    private final TaskWorkLogRepository repository;
    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;

    public TaskWorkLogService(TaskWorkLogRepository repository,
                              TaskRepository taskRepository,
                              OutboxRepository outboxRepository,
                              UserClient userClient,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.userClient = userClient;
        this.objectMapper = objectMapper;
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
     * Publishes a WORK_LOG_CREATED audit event.
     */
    @Transactional
    public TaskWorkLogResponse create(UUID taskId, TaskWorkLogRequest request) {
        assertTaskExists(taskId);
        UserDto user = userClient.getUserById(request.getUserId());
        TaskWorkLog saved = repository.save(TaskWorkLog.builder()
                .taskId(taskId)
                .userId(request.getUserId())
                .workType(request.getWorkType())
                .plannedHours(request.getPlannedHours() != null ? request.getPlannedHours().intValue() : 0)
                .bookedHours(request.getBookedHours()  != null ? request.getBookedHours().intValue()  : 0)
                .createdAt(Instant.now())
                .build());
        writeToOutbox(TaskChangedEvent.workLogCreated(taskId, saved.getId(), saved.getUserId(),
                saved.getWorkType(), BigInteger.valueOf(saved.getPlannedHours()), BigInteger.valueOf(saved.getBookedHours())));
        return toResponse(saved, user.getName());
    }

    /**
     * Updates the userId, workType, and bookedHours of an existing work log entry.
     * plannedHours is immutable once set and is intentionally not updated here.
     * Publishes a WORK_LOG_UPDATED audit event.
     */
    @Transactional
    public TaskWorkLogResponse update(UUID taskId, UUID workLogId, TaskWorkLogRequest request) {
        assertTaskExists(taskId);
        TaskWorkLog log = getOrThrow(workLogId);
        UserDto user = userClient.getUserById(request.getUserId());
        log.setUserId(request.getUserId());
        log.setWorkType(request.getWorkType());
        log.setBookedHours(request.getBookedHours() != null ? request.getBookedHours().intValue() : 0);
        // plannedHours is immutable: intentionally not updated
        TaskWorkLog saved = repository.save(log);
        writeToOutbox(TaskChangedEvent.workLogUpdated(taskId, saved.getId(), saved.getUserId(),
                saved.getWorkType(), BigInteger.valueOf(saved.getPlannedHours()), BigInteger.valueOf(saved.getBookedHours())));
        return toResponse(saved, user.getName());
    }

    /**
     * Soft-deletes a work log entry; throws if not found.
     * Publishes a WORK_LOG_DELETED audit event.
     */
    @Transactional
    public void delete(UUID taskId, UUID workLogId) {
        assertTaskExists(taskId);
        if (!repository.existsById(workLogId)) {
            throw new ResourceNotFoundException("TaskWorkLog", workLogId);
        }
        repository.deleteById(workLogId);
        writeToOutbox(TaskChangedEvent.workLogDeleted(taskId, workLogId));
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
                BigInteger.valueOf(log.getPlannedHours()),
                BigInteger.valueOf(log.getBookedHours()),
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

    /** Serializes the event to JSON and saves it to the outbox table within the current transaction. */
    private void writeToOutbox(TaskChangedEvent event) {
        try {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(event.getTaskId())
                    .eventType(OutboxEventType.TASK_CHANGED)
                    .topic(OutboxPublisher.TOPIC)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize work log outbox event", e);
        }
    }
}

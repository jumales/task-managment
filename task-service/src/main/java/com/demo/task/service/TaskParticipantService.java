package com.demo.task.service;

import com.demo.common.dto.TaskParticipantRequest;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.dto.TaskParticipantRole;
import com.demo.common.dto.UserDto;
import com.demo.common.exception.DuplicateResourceException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.TaskParticipant;
import com.demo.task.repository.TaskParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages task participants: adding, removing, and querying user-role associations per task.
 */
@Service
public class TaskParticipantService {

    private final TaskParticipantRepository repository;
    private final UserClient userClient;
    private final UserClientHelper userClientHelper;

    public TaskParticipantService(TaskParticipantRepository repository,
                                  UserClient userClient,
                                  UserClientHelper userClientHelper) {
        this.repository = repository;
        this.userClient = userClient;
        this.userClientHelper = userClientHelper;
    }

    /**
     * Adds a participant with the given role to the task.
     * Throws 400 if the CREATOR role is requested (set automatically at task creation only).
     * Throws 409 if the user already holds that role on the task.
     */
    @Transactional
    public TaskParticipantResponse add(UUID taskId, TaskParticipantRequest request) {
        if (request.getRole() == TaskParticipantRole.CREATOR) {
            throw new IllegalArgumentException("CREATOR role is assigned automatically at task creation and cannot be added manually");
        }
        if (repository.existsByTaskIdAndUserIdAndRole(taskId, request.getUserId(), request.getRole())) {
            throw new DuplicateResourceException(
                    "User already has role " + request.getRole() + " on this task");
        }
        UserDto user = userClient.getUserById(request.getUserId());
        TaskParticipant saved = repository.save(TaskParticipant.builder()
                .taskId(taskId)
                .userId(request.getUserId())
                .role(request.getRole())
                .createdAt(Instant.now())
                .build());
        return toResponse(saved, user);
    }

    /** Soft-deletes a participant by ID; throws if not found. */
    @Transactional
    public void remove(UUID participantId) {
        if (!repository.existsById(participantId)) {
            throw new ResourceNotFoundException("TaskParticipant", participantId);
        }
        repository.deleteById(participantId);
    }

    /** Returns all participants for a task, enriched with user details. */
    public List<TaskParticipantResponse> findByTaskId(UUID taskId) {
        List<TaskParticipant> participants = repository.findByTaskId(taskId);
        return enrich(participants);
    }

    /**
     * Batch-loads participants for multiple tasks and returns them grouped by task ID.
     * Uses a single repository query and a single batch user-service call.
     */
    public Map<UUID, List<TaskParticipantResponse>> findByTaskIds(Set<UUID> taskIds) {
        if (taskIds.isEmpty()) return Map.of();
        List<TaskParticipant> all = repository.findByTaskIdIn(taskIds);
        List<TaskParticipantResponse> enriched = enrich(all);

        // Re-associate enriched responses with their task IDs using the original participant list
        Map<UUID, UUID> participantToTask = all.stream()
                .collect(Collectors.toMap(TaskParticipant::getId, TaskParticipant::getTaskId));

        return enriched.stream()
                .collect(Collectors.groupingBy(r -> participantToTask.get(r.getId())));
    }

    /**
     * Creates a CREATOR participant for the given user on the task.
     * Only called once at task creation; does nothing if userId is null.
     */
    @Transactional
    public void setCreator(UUID taskId, UUID userId) {
        if (userId == null) return;
        repository.save(TaskParticipant.builder()
                .taskId(taskId)
                .userId(userId)
                .role(TaskParticipantRole.CREATOR)
                .createdAt(Instant.now())
                .build());
    }

    /**
     * Creates an ASSIGNEE participant for the given user on the task.
     * Soft-deletes any existing ASSIGNEE first, then flushes to avoid a unique-constraint
     * violation when the new ASSIGNEE carries the same user ID as the old one.
     */
    @Transactional
    public void setAssignee(UUID taskId, UUID userId) {
        if (userId == null) return;
        // Remove existing ASSIGNEE if present, then flush to avoid unique-index conflicts on re-assign
        repository.deleteByTaskIdAndRole(taskId, TaskParticipantRole.ASSIGNEE);
        repository.flush();
        repository.save(TaskParticipant.builder()
                .taskId(taskId)
                .userId(userId)
                .role(TaskParticipantRole.ASSIGNEE)
                .createdAt(Instant.now())
                .build());
    }

    /** Enriches a list of participants with user names and emails from user-service. */
    private List<TaskParticipantResponse> enrich(List<TaskParticipant> participants) {
        if (participants.isEmpty()) return List.of();
        Set<UUID> userIds = participants.stream().map(TaskParticipant::getUserId).collect(Collectors.toSet());
        Map<UUID, UserDto> usersById = userClientHelper.fetchUsers(userIds);
        return participants.stream()
                .map(p -> {
                    UserDto u = usersById.get(p.getUserId());
                    return toResponse(p, u);
                })
                .toList();
    }

    private TaskParticipantResponse toResponse(TaskParticipant p, UserDto user) {
        return new TaskParticipantResponse(
                p.getId(),
                p.getUserId(),
                user != null ? user.getName() : null,
                user != null ? user.getEmail() : null,
                p.getRole());
    }

}

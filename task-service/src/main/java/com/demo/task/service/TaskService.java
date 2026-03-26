package com.demo.task.service;

import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.model.OutboxEvent;
import com.demo.task.model.OutboxEventType;
import com.demo.task.model.Task;
import com.demo.task.model.TaskComment;
import com.demo.task.model.TaskPhase;
import com.demo.task.model.TaskProject;
import com.demo.task.outbox.OutboxPublisher;
import com.demo.task.repository.OutboxRepository;
import com.demo.task.repository.TaskCommentRepository;
import com.demo.task.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private static final String AGGREGATE_TYPE = "Task";

    private final TaskRepository repository;
    private final TaskCommentRepository commentRepository;
    private final OutboxRepository outboxRepository;
    private final UserClient userClient;
    private final TaskProjectService projectService;
    private final TaskPhaseService phaseService;
    private final ObjectMapper objectMapper;

    public TaskService(TaskRepository repository,
                       TaskCommentRepository commentRepository,
                       OutboxRepository outboxRepository,
                       UserClient userClient,
                       TaskProjectService projectService,
                       TaskPhaseService phaseService,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.outboxRepository = outboxRepository;
        this.userClient = userClient;
        this.projectService = projectService;
        this.phaseService = phaseService;
        this.objectMapper = objectMapper;
    }

    /** Returns all tasks. */
    public List<TaskResponse> findAll() {
        return toResponseList(repository.findAll());
    }

    /** Returns the task with the given ID, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public TaskResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /** Returns all tasks assigned to the specified user. */
    public List<TaskResponse> findByUser(UUID userId) {
        return toResponseList(repository.findByAssignedUserId(userId));
    }

    /**
     * Returns all tasks whose status matches the given value (case-insensitive).
     *
     * @param status string representation of {@link TaskStatus}
     */
    public List<TaskResponse> findByStatus(String status) {
        return toResponseList(repository.findByStatus(TaskStatus.valueOf(status.toUpperCase())));
    }

    /** Returns all tasks belonging to the specified project. */
    public List<TaskResponse> findByProject(UUID projectId) {
        projectService.getOrThrow(projectId);
        return toResponseList(repository.findByProjectId(projectId));
    }

    /** Creates a new task, resolving the phase and validating the assigned user and project. */
    public TaskResponse create(TaskRequest request) {
        userClient.getUserById(request.getAssignedUserId());
        projectService.getOrThrow(request.getProjectId());

        // Use the explicitly requested phase, or fall back to the project's default phase.
        UUID phaseId = resolvePhaseId(request.getPhaseId(), request.getProjectId());

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .assignedUserId(request.getAssignedUserId())
                .projectId(request.getProjectId())
                .phaseId(phaseId)
                .build();
        return toResponse(repository.save(task));
    }

    /** Updates the task fields and writes outbox events for any status or phase change. */
    @Transactional
    public TaskResponse update(UUID id, TaskRequest request) {
        Task task = getOrThrow(id);

        TaskStatus oldStatus = task.getStatus();
        UUID oldPhaseId = task.getPhaseId();

        if (!task.getProjectId().equals(request.getProjectId())) {
            projectService.getOrThrow(request.getProjectId());
        }
        if (request.getPhaseId() != null && !request.getPhaseId().equals(oldPhaseId)) {
            phaseService.getOrThrow(request.getPhaseId());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setAssignedUserId(request.getAssignedUserId());
        task.setProjectId(request.getProjectId());
        task.setPhaseId(request.getPhaseId());
        Task saved = repository.save(task);

        publishOutboxEvents(saved, oldStatus, request.getStatus(), oldPhaseId, request.getPhaseId());

        return toResponse(saved);
    }

    /** Publishes status-changed and phase-changed outbox events when the respective values differ. */
    private void publishOutboxEvents(Task saved, TaskStatus oldStatus, TaskStatus newStatus,
                                     UUID oldPhaseId, UUID newPhaseId) {
        if (newStatus != null && !newStatus.equals(oldStatus)) {
            writeToOutbox(TaskChangedEvent.statusChanged(saved.getId(), saved.getAssignedUserId(), oldStatus, newStatus));
        }
        if (!Objects.equals(oldPhaseId, newPhaseId)) {
            String oldName = oldPhaseId != null ? phaseService.getOrThrow(oldPhaseId).getName() : null;
            String newName = newPhaseId != null ? phaseService.getOrThrow(newPhaseId).getName() : null;
            writeToOutbox(TaskChangedEvent.phaseChanged(saved.getId(), saved.getAssignedUserId(),
                    oldPhaseId, oldName, newPhaseId, newName));
        }
    }

    /** Returns all comments for the given task, ordered by creation time ascending. */
    public List<TaskCommentResponse> getComments(UUID taskId) {
        getOrThrow(taskId);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(c -> new TaskCommentResponse(c.getId(), c.getContent(), c.getCreatedAt()))
                .toList();
    }

    /** Adds a comment to the task, publishes a {@code COMMENT_ADDED} outbox event, and returns the created comment. */
    @Transactional
    public TaskCommentResponse addComment(UUID taskId, TaskCommentRequest request) {
        Task task = getOrThrow(taskId);
        TaskComment saved = commentRepository.save(TaskComment.builder()
                .taskId(taskId)
                .content(request.getContent())
                .createdAt(Instant.now())
                .build());

        writeToOutbox(TaskChangedEvent.commentAdded(taskId, task.getAssignedUserId(), saved.getId(), saved.getContent()));

        return new TaskCommentResponse(saved.getId(), saved.getContent(), saved.getCreatedAt());
    }

    /** Soft-deletes the task; throws if the task has any associated comments. */
    @Transactional
    public void delete(UUID id) {
        getOrThrow(id);
        if (commentRepository.existsByTaskId(id)) {
            throw new RelatedEntityActiveException("Task", "comments");
        }
        repository.deleteById(id);
    }

    // Serializes the event to JSON and saves it to the outbox table within the current transaction.
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
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    private Task getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    /** Returns the explicit phaseId if provided, otherwise the project's default phase id (or null). */
    private UUID resolvePhaseId(UUID requestedPhaseId, UUID projectId) {
        if (requestedPhaseId != null) {
            phaseService.getOrThrow(requestedPhaseId);
            return requestedPhaseId;
        }
        return phaseService.findDefaultForProject(projectId).map(TaskPhase::getId).orElse(null);
    }

    /**
     * Converts a single task to its response DTO, fetching related data individually.
     * Use for single-task endpoints (findById, create, update) to keep the code simple.
     */
    private TaskResponse toResponse(Task task) {
        UserDto user = null;
        try {
            user = userClient.getUserById(task.getAssignedUserId());
        } catch (Exception e) {
            // user-service unavailable — return task without user details
        }
        TaskProjectResponse project = projectService.toResponse(projectService.getOrThrow(task.getProjectId()));
        TaskPhaseResponse phase = task.getPhaseId() != null
                ? phaseService.toResponse(phaseService.getOrThrow(task.getPhaseId()))
                : null;
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), user, project, phase);
    }

    /**
     * Converts a list of tasks to response DTOs using a single batch query per related entity,
     * avoiding N+1 database round-trips for projects and phases.
     */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<UUID> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Set<UUID> phaseIds   = tasks.stream().map(Task::getPhaseId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, TaskProjectResponse> projectsById = projectService.findAllByIds(projectIds)
                .stream()
                .collect(Collectors.toMap(TaskProject::getId, projectService::toResponse));

        Map<UUID, TaskPhaseResponse> phasesById = phaseIds.isEmpty() ? Map.of() :
                phaseService.findAllByIds(phaseIds)
                        .stream()
                        .collect(Collectors.toMap(TaskPhase::getId, phaseService::toResponse));

        return tasks.stream().map(task -> {
            UserDto user = null;
            try {
                user = userClient.getUserById(task.getAssignedUserId());
            } catch (Exception e) {
                // user-service unavailable — return task without user details
            }
            return new TaskResponse(
                    task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                    user,
                    projectsById.get(task.getProjectId()),
                    task.getPhaseId() != null ? phasesById.get(task.getPhaseId()) : null);
        }).toList();
    }
}

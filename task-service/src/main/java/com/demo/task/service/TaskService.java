package com.demo.task.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.config.KafkaTopics;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.event.TaskEvent;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.OutboxEvent;
import com.demo.task.model.OutboxEventType;
import com.demo.task.model.Task;
import com.demo.task.model.TaskComment;
import com.demo.task.model.TaskPhase;
import com.demo.task.model.TaskProject;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.OutboxRepository;
import com.demo.task.repository.TaskCommentRepository;
import com.demo.task.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final UserClientHelper userClientHelper;
    private final TaskProjectService projectService;
    private final TaskPhaseService phaseService;
    private final TaskParticipantService participantService;
    private final TaskTimelineService timelineService;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public TaskService(TaskRepository repository,
                       TaskCommentRepository commentRepository,
                       OutboxRepository outboxRepository,
                       UserClient userClient,
                       UserClientHelper userClientHelper,
                       TaskProjectService projectService,
                       TaskPhaseService phaseService,
                       TaskParticipantService participantService,
                       TaskTimelineService timelineService,
                       OutboxWriter outboxWriter,
                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.outboxRepository = outboxRepository;
        this.userClient = userClient;
        this.userClientHelper = userClientHelper;
        this.projectService = projectService;
        this.phaseService = phaseService;
        this.participantService = participantService;
        this.timelineService = timelineService;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    /** Returns a paginated summary page of all tasks. */
    public PageResponse<TaskSummaryResponse> findAll(Pageable pageable) {
        Page<Task> page = repository.findAll(pageable);
        return toSummaryPageResponse(page);
    }

    /** Returns the task with the given ID, or throws {@link com.demo.common.exception.ResourceNotFoundException}. */
    public TaskResponse findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    /** Returns a paginated summary page of tasks assigned to the specified user. */
    public PageResponse<TaskSummaryResponse> findByUser(UUID userId, Pageable pageable) {
        Page<Task> page = repository.findByAssignedUserId(userId, pageable);
        return toSummaryPageResponse(page);
    }

    /**
     * Returns a paginated summary page of tasks whose status matches the given value (case-insensitive).
     *
     * @param status string representation of {@link TaskStatus}
     */
    public PageResponse<TaskSummaryResponse> findByStatus(String status, Pageable pageable) {
        Page<Task> page = repository.findByStatus(TaskStatus.valueOf(status.toUpperCase()), pageable);
        return toSummaryPageResponse(page);
    }

    /** Returns a paginated summary page of tasks belonging to the specified project. */
    public PageResponse<TaskSummaryResponse> findByProject(UUID projectId, Pageable pageable) {
        projectService.getOrThrow(projectId);
        Page<Task> page = repository.findByProjectId(projectId, pageable);
        return toSummaryPageResponse(page);
    }

    /**
     * Creates a new task, resolving the phase, validating the assigned user and project,
     * recording both the CREATOR and ASSIGNEE participants, and initializing the mandatory
     * PLANNED_START and PLANNED_END timeline entries.
     *
     * @param creatorId the authenticated user's ID — becomes the CREATOR participant and timeline entry author
     */
    @Transactional
    public TaskResponse create(TaskRequest request, UUID creatorId) {
        validatePlannedDates(request.getPlannedStart(), request.getPlannedEnd());
        UserDto user = userClient.getUserById(request.getAssignedUserId());
        TaskProject project = projectService.getOrThrow(request.getProjectId());

        // Use the explicitly requested phase, or fall back to the project's default phase.
        UUID phaseId = resolvePhaseId(request.getPhaseId(), project);

        String taskCode = projectService.nextTaskCode(request.getProjectId());

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .type(request.getType())
                .progress(request.getProgress())
                .assignedUserId(request.getAssignedUserId())
                .projectId(request.getProjectId())
                .phaseId(phaseId)
                .taskCode(taskCode)
                .build();
        Task saved = repository.save(task);
        participantService.setCreator(saved.getId(), creatorId);
        participantService.setAssignee(saved.getId(), request.getAssignedUserId());
        timelineService.createInitialTimelines(saved.getId(), request.getPlannedStart(), request.getPlannedEnd(), creatorId);
        writeTaskLifecycleOutboxEvent(saved, OutboxEventType.TASK_CREATED,
                project.getName(), phaseId, user.getName());
        outboxWriter.write(TaskChangedEvent.taskCreated(saved.getId(), saved.getAssignedUserId(),
                saved.getProjectId(), saved.getTitle()));
        return toResponse(saved);
    }

    /**
     * Validates that both planned dates are present and that plannedStart is strictly before plannedEnd.
     */
    private void validatePlannedDates(java.time.Instant plannedStart, java.time.Instant plannedEnd) {
        if (plannedStart == null) {
            throw new IllegalArgumentException("plannedStart is required");
        }
        if (plannedEnd == null) {
            throw new IllegalArgumentException("plannedEnd is required");
        }
        if (!plannedStart.isBefore(plannedEnd)) {
            throw new IllegalArgumentException("plannedStart must be before plannedEnd");
        }
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
        if (request.getPhaseId() == null) {
            throw new IllegalArgumentException("phaseId is required");
        }
        if (!request.getPhaseId().equals(oldPhaseId)) {
            phaseService.getOrThrow(request.getPhaseId());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setType(request.getType());
        task.setProgress(request.getProgress());
        task.setAssignedUserId(request.getAssignedUserId());
        task.setProjectId(request.getProjectId());
        task.setPhaseId(request.getPhaseId());
        Task saved = repository.save(task);

        participantService.setAssignee(saved.getId(), request.getAssignedUserId());
        publishOutboxEvents(saved, oldStatus, request.getStatus(), oldPhaseId, request.getPhaseId());

        // Publish lifecycle event to task-events topic so search-service keeps its index current.
        TaskProject project = projectService.getOrThrow(saved.getProjectId());
        String userName = userClientHelper.resolveUserName(saved.getAssignedUserId());
        writeTaskLifecycleOutboxEvent(saved, OutboxEventType.TASK_UPDATED,
                project.getName(), saved.getPhaseId(), userName);

        return toResponse(saved);
    }

    /** Publishes status-changed and phase-changed outbox events when the respective values differ. */
    private void publishOutboxEvents(Task saved, TaskStatus oldStatus, TaskStatus newStatus,
                                     UUID oldPhaseId, UUID newPhaseId) {
        if (newStatus != null && !newStatus.equals(oldStatus)) {
            outboxWriter.write(TaskChangedEvent.statusChanged(saved.getId(), saved.getAssignedUserId(),
                    saved.getProjectId(), saved.getTitle(), oldStatus, newStatus));
        }
        if (!Objects.equals(oldPhaseId, newPhaseId)) {
            // getName() returns TaskPhaseName enum; .name() converts to String for the event payload
            String oldName = oldPhaseId != null ? phaseService.getOrThrow(oldPhaseId).getName().name() : null;
            String newName = newPhaseId != null ? phaseService.getOrThrow(newPhaseId).getName().name() : null;
            outboxWriter.write(TaskChangedEvent.phaseChanged(saved.getId(), saved.getAssignedUserId(),
                    saved.getProjectId(), saved.getTitle(), oldPhaseId, oldName, newPhaseId, newName));
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

        outboxWriter.write(TaskChangedEvent.commentAdded(taskId, task.getAssignedUserId(),
                task.getProjectId(), task.getTitle(), saved.getId(), saved.getContent()));

        return new TaskCommentResponse(saved.getId(), saved.getContent(), saved.getCreatedAt());
    }

    /** Soft-deletes the task; throws if the task has any associated comments. */
    @Transactional
    public void delete(UUID id) {
        getOrThrow(id);
        if (commentRepository.existsByTaskId(id)) {
            throw new RelatedEntityActiveException("Task", "comments");
        }
        // Write the lifecycle event before the soft-delete so the task ID is still in context.
        writeTaskDeletedOutboxEvent(id);
        repository.deleteById(id);
    }

    /**
     * Writes a TASK_CREATED or TASK_UPDATED lifecycle event to the outbox so search-service
     * can update its Elasticsearch index.
     */
    private void writeTaskLifecycleOutboxEvent(Task task, OutboxEventType eventType,
                                               String projectName, UUID phaseId, String userName) {
        // getName() returns TaskPhaseName enum; .name() converts to String for the event payload
        String phaseName = phaseId != null ? phaseService.getOrThrow(phaseId).getName().name() : null;
        TaskEvent event = switch (eventType) {
            case TASK_CREATED -> TaskEvent.created(task.getId(), task.getTitle(), task.getDescription(),
                    task.getStatus(), task.getProjectId(), projectName,
                    phaseId, phaseName, task.getAssignedUserId(), userName);
            case TASK_UPDATED -> TaskEvent.updated(task.getId(), task.getTitle(), task.getDescription(),
                    task.getStatus(), task.getProjectId(), projectName,
                    phaseId, phaseName, task.getAssignedUserId(), userName);
            default -> throw new IllegalArgumentException("Unexpected lifecycle event type: " + eventType);
        };
        writeLifecycleToOutbox(task.getId(), eventType, event);
    }

    /** Writes a TASK_DELETED lifecycle event to the outbox so search-service can remove the document. */
    private void writeTaskDeletedOutboxEvent(UUID taskId) {
        writeLifecycleToOutbox(taskId, OutboxEventType.TASK_DELETED, TaskEvent.deleted(taskId));
    }

    /** Serializes a lifecycle event to JSON and saves it to the outbox for the {@code task-events} topic. */
    private void writeLifecycleToOutbox(UUID taskId, OutboxEventType eventType, TaskEvent event) {
        try {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(taskId)
                    .eventType(eventType)
                    .topic(KafkaTopics.TASK_EVENTS)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task lifecycle outbox event", e);
        }
    }

    private Task getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    /**
     * Returns the explicit phaseId if provided, otherwise the project's configured default phase id.
     * Throws {@link IllegalArgumentException} when neither is available, since phase is mandatory.
     */
    private UUID resolvePhaseId(UUID requestedPhaseId, TaskProject project) {
        if (requestedPhaseId != null) {
            phaseService.getOrThrow(requestedPhaseId);
            return requestedPhaseId;
        }
        if (project.getDefaultPhaseId() != null) {
            return project.getDefaultPhaseId();
        }
        throw new IllegalArgumentException(
                "phaseId is required: the project has no default phase configured");
    }

    /**
     * Converts a single task to its response DTO, fetching participants and related data individually.
     * Use for single-task endpoints (findById, create, update).
     */
    private TaskResponse toResponse(Task task) {
        List<TaskParticipantResponse> participants = participantService.findByTaskId(task.getId());
        TaskProjectResponse project = projectService.toResponse(projectService.getOrThrow(task.getProjectId()));
        TaskPhaseResponse phase = phaseService.toResponse(phaseService.getOrThrow(task.getPhaseId()));
        return new TaskResponse(task.getId(), task.getTaskCode(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getType(), task.getProgress(), participants, project, phase);
    }

    /**
     * Converts a list of tasks to response DTOs using batch queries for projects, phases, and participants,
     * avoiding N+1 database and HTTP round-trips.
     */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<UUID> taskIds    = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        Set<UUID> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Set<UUID> phaseIds   = tasks.stream().map(Task::getPhaseId).collect(Collectors.toSet());

        Map<UUID, TaskProjectResponse> projectsById = projectService.findAllByIds(projectIds)
                .stream()
                .collect(Collectors.toMap(TaskProject::getId, projectService::toResponse));

        Map<UUID, TaskPhaseResponse> phasesById = phaseService.findAllByIds(phaseIds)
                .stream()
                .collect(Collectors.toMap(TaskPhase::getId, phaseService::toResponse));

        // Batch-load all participants for these tasks in two queries (DB + user-service batch)
        Map<UUID, List<TaskParticipantResponse>> participantsByTaskId =
                participantService.findByTaskIds(taskIds);

        return tasks.stream().map(task -> new TaskResponse(
                task.getId(), task.getTaskCode(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getType(), task.getProgress(),
                participantsByTaskId.getOrDefault(task.getId(), List.of()),
                projectsById.get(task.getProjectId()),
                phasesById.get(task.getPhaseId())))
                .toList();
    }

    /** Converts a {@link Page} of tasks to a {@link PageResponse} with mapped response DTOs. */
    private PageResponse<TaskResponse> toPageResponse(Page<Task> page) {
        return new PageResponse<>(
                toResponseList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /**
     * Converts a list of tasks to lightweight summary DTOs using batch queries for projects,
     * phases, and user names — no per-task participant loading.
     */
    private List<TaskSummaryResponse> toSummaryResponseList(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<UUID> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Set<UUID> phaseIds   = tasks.stream().map(Task::getPhaseId).collect(Collectors.toSet());
        Set<UUID> userIds    = tasks.stream().map(Task::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, TaskProject> projectsById = projectService.findAllByIds(projectIds).stream()
                .collect(Collectors.toMap(TaskProject::getId, p -> p));

        Map<UUID, TaskPhase> phasesById = phaseService.findAllByIds(phaseIds).stream()
                .collect(Collectors.toMap(TaskPhase::getId, p -> p));

        Map<UUID, String> userNamesById = userIds.isEmpty() ? Map.of() :
                userClientHelper.fetchUserNames(userIds);

        return tasks.stream().map(task -> {
            TaskProject project = projectsById.get(task.getProjectId());
            TaskPhase phase = phasesById.get(task.getPhaseId());
            return new TaskSummaryResponse(
                    task.getId(),
                    task.getTaskCode(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getType(),
                    task.getProgress(),
                    task.getAssignedUserId(),
                    userNamesById.get(task.getAssignedUserId()),
                    project != null ? project.getId() : null,
                    project != null ? project.getName() : null,
                    phase != null ? phase.getId() : null,
                    // getName() returns TaskPhaseName enum; .name() converts to String for the response
                    phase != null ? phase.getName().name() : null);
        }).toList();
    }

    /** Converts a {@link Page} of tasks to a {@link PageResponse} of summary DTOs. */
    private PageResponse<TaskSummaryResponse> toSummaryPageResponse(Page<Task> page) {
        return new PageResponse<>(
                toSummaryResponseList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}

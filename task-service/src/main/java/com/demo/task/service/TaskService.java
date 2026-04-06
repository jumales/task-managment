package com.demo.task.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskBookedWorkResponse;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.PlannedDatesRequest;
import com.demo.common.dto.TaskFullResponse;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.dto.TaskPhaseResponse;
import com.demo.common.dto.TaskPlannedWorkResponse;
import com.demo.common.dto.TaskProjectResponse;
import com.demo.common.dto.TaskRequest;
import com.demo.common.dto.TaskResponse;
import com.demo.common.dto.TaskPhaseName;
import com.demo.common.dto.TaskStatus;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.common.dto.TaskTimelineResponse;
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
import java.util.concurrent.CompletableFuture;
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
    private final TaskPlannedWorkService plannedWorkService;
    private final TaskBookedWorkService bookedWorkService;
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
                       TaskPlannedWorkService plannedWorkService,
                       TaskBookedWorkService bookedWorkService,
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
        this.plannedWorkService = plannedWorkService;
        this.bookedWorkService = bookedWorkService;
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

    /**
     * Returns the full task view for the given ID, including timeline, planned work, and booked work.
     * Timeline, planned work, and booked work are fetched concurrently to reduce overall latency.
     * Throws {@link com.demo.common.exception.ResourceNotFoundException} when the task does not exist.
     */
    public TaskFullResponse findFullById(UUID id) {
        Task task = getOrThrow(id);

        // Fetch sub-collections concurrently; each call is independent and I/O-bound.
        CompletableFuture<List<TaskTimelineResponse>> timelinesFuture =
                CompletableFuture.supplyAsync(() -> timelineService.findByTaskId(id));
        CompletableFuture<List<TaskPlannedWorkResponse>> plannedWorkFuture =
                CompletableFuture.supplyAsync(() -> plannedWorkService.findByTaskId(id));
        CompletableFuture<List<TaskBookedWorkResponse>> bookedWorkFuture =
                CompletableFuture.supplyAsync(() -> bookedWorkService.findByTaskId(id));

        // Fetch synchronous data while the async calls are running.
        TaskBaseData base = fetchBaseData(task);
        // Fetch full user profile; null when no user is assigned or user-service is unavailable.
        UserDto assignedUser = userClientHelper.fetchUser(task.getAssignedUserId());
        //TODO: what is situation if timelines, plannedwork or bookedwork stoped? Will this thread block? Does we need set timeout?
        return new TaskFullResponse(
                task.getId(), task.getTaskCode(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getType(), task.getProgress(),
                base.participants(), base.project(), base.phase(), assignedUser,
                timelinesFuture.join(), plannedWorkFuture.join(), bookedWorkFuture.join());
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

        // Every new task starts in the PLANNING phase; any phaseId in the request is ignored.
        UUID phaseId = phaseService.findPlanningPhaseOrThrow(project.getId()).getId();

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
        //TODO all three items (creator, assignee, timeline) needs to be done async without blocking thread
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

    /** Updates task fields (title, description, status, type, progress, assignee, project). Phase is unchanged — use {@code updatePhase} for that. */
    @Transactional
    public TaskResponse update(UUID id, TaskRequest request) {
        Task task = getOrThrow(id);

        TaskStatus oldStatus = task.getStatus();

        if (!task.getProjectId().equals(request.getProjectId())) {
            projectService.getOrThrow(request.getProjectId());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setType(request.getType());
        task.setProgress(request.getProgress());
        task.setAssignedUserId(request.getAssignedUserId());
        task.setProjectId(request.getProjectId());
        Task saved = repository.save(task);

        participantService.setAssignee(saved.getId(), request.getAssignedUserId());
        publishStatusChangedEvent(saved, oldStatus, request.getStatus());

        // Publish lifecycle event to task-events topic so search-service keeps its index current.
        TaskProject project = projectService.getOrThrow(saved.getProjectId());
        String userName = userClientHelper.resolveUserName(saved.getAssignedUserId());
        writeTaskLifecycleOutboxEvent(saved, OutboxEventType.TASK_UPDATED,
                project.getName(), saved.getPhaseId(), userName);

        return toResponse(saved);
    }

    /**
     * Changes the phase of a task and publishes a PHASE_CHANGED outbox event.
     * Enforces the one-way gate: a task that has left PLANNING may never return to it.
     */
    @Transactional
    public TaskResponse updatePhase(UUID id, UUID phaseId) {
        Task task = getOrThrow(id);
        UUID oldPhaseId = task.getPhaseId();

        TaskPhase currentPhase = phaseService.getOrThrow(oldPhaseId);
        TaskPhase newPhase     = phaseService.getOrThrow(phaseId);

        // One-way gate: once a task has left PLANNING it may never return.
        // TODO use equals for comparing strings
        if (currentPhase.getName() != TaskPhaseName.PLANNING && newPhase.getName() == TaskPhaseName.PLANNING) {
            throw new IllegalArgumentException("Cannot return a task to the PLANNING phase");
        }

        task.setPhaseId(phaseId);
        Task saved = repository.save(task);

        outboxWriter.write(TaskChangedEvent.phaseChanged(
                saved.getId(), saved.getAssignedUserId(), saved.getProjectId(), saved.getTitle(),
                oldPhaseId, currentPhase.getName().name(),
                phaseId,    newPhase.getName().name()));

        // Publish lifecycle event so search-service keeps its index current.
        TaskProject project  = projectService.getOrThrow(saved.getProjectId());
        String userName      = userClientHelper.resolveUserName(saved.getAssignedUserId());
        writeTaskLifecycleOutboxEvent(saved, OutboxEventType.TASK_UPDATED,
                project.getName(), phaseId, userName);

        return toResponse(saved);
    }

    /** Publishes a STATUS_CHANGED outbox event when the status has actually changed. */
    private void publishStatusChangedEvent(Task saved, TaskStatus oldStatus, TaskStatus newStatus) {
        //TODO rewrite on way that outboxWriter isn't in if
        if (newStatus != null && !newStatus.equals(oldStatus)) {
            outboxWriter.write(TaskChangedEvent.statusChanged(saved.getId(), saved.getAssignedUserId(),
                    saved.getProjectId(), saved.getTitle(), oldStatus, newStatus));
        }
    }

    /**
     * Returns all comments for the given task, ordered by creation time ascending.
     * Each comment is enriched with the author's display name via a single batch call.
     */
    public List<TaskCommentResponse> getComments(UUID taskId) {
        getOrThrow(taskId);
        List<TaskComment> comments = commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
        if (comments.isEmpty()) return List.of();

        // Batch-resolve user names in one call; skips null user IDs (legacy comments).
        Set<UUID> authorIds = comments.stream()
                .map(TaskComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> nameById = authorIds.isEmpty() ? Map.of() : userClientHelper.fetchUserNames(authorIds);

        return comments.stream()
                .map(c -> new TaskCommentResponse(c.getId(), c.getUserId(),
                        nameById.get(c.getUserId()), c.getContent(), c.getCreatedAt()))
                .toList();
    }

    /**
     * Adds a comment to the task authored by the given user,
     * publishes a {@code COMMENT_ADDED} outbox event, and returns the created comment.
     *
     * @param authorId the authenticated user's UUID — stored on the comment for display purposes
     */
    @Transactional
    public TaskCommentResponse addComment(UUID taskId, TaskCommentRequest request, UUID authorId) {
        Task task = getOrThrow(taskId);
        TaskComment saved = commentRepository.save(TaskComment.builder()
                .taskId(taskId)
                .userId(authorId)
                .content(request.getContent())
                .createdAt(Instant.now())
                .build());

        outboxWriter.write(TaskChangedEvent.commentAdded(taskId, task.getAssignedUserId(),
                task.getProjectId(), task.getTitle(), saved.getId(), saved.getContent()));

        String authorName = authorId != null ? userClientHelper.resolveUserName(authorId) : null;
        return new TaskCommentResponse(saved.getId(), saved.getUserId(), authorName,
                saved.getContent(), saved.getCreatedAt());
    }

    /**
     * Updates the planned start and end dates for a task.
     * Throws {@link IllegalArgumentException} if the task is not in the PLANNING phase.
     *
     * @param updatingUserId the authenticated user performing the update
     */
    public TaskFullResponse updatePlannedDates(UUID taskId, UUID updatingUserId, PlannedDatesRequest request) {
        Task task = getOrThrow(taskId);
        TaskPhaseName phaseName = phaseService.getOrThrow(task.getPhaseId()).getName();
        //TODO use equals for compare
        if (phaseName != TaskPhaseName.PLANNING) {
            throw new IllegalArgumentException("Planned dates can only be changed while the task is in the PLANNING phase");
        }
        timelineService.updatePlannedDates(taskId, request.getPlannedStart(), request.getPlannedEnd(), updatingUserId);
        return findFullById(taskId);
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
     * Converts a single task to its response DTO, fetching participants and related data individually.
     * Use for single-task endpoints (findById, create, update).
     */
    private TaskResponse toResponse(Task task) {
        TaskBaseData base = fetchBaseData(task);
        return new TaskResponse(task.getId(), task.getTaskCode(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getType(), task.getProgress(),
                base.participants(), base.project(), base.phase());
    }

    /** Fetches participants, project, and phase for a single task — shared by toResponse and findFullById. */
    private TaskBaseData fetchBaseData(Task task) {
        return new TaskBaseData(
                participantService.findByTaskId(task.getId()),
                projectService.toResponse(projectService.getOrThrow(task.getProjectId())), //TODO make toResponse private. Create response with projectId and return project
                phaseService.toResponse(phaseService.getOrThrow(task.getPhaseId())));//TODO make toResponse private. Create response with phaseId and return phase
    }

    /** Bundles the three sub-fetches that are common to both the standard and full task responses. */
    private record TaskBaseData(List<TaskParticipantResponse> participants,
                                TaskProjectResponse project,
                                TaskPhaseResponse phase) {}

    /**
     * Converts a list of tasks to response DTOs using batch queries for projects, phases, and participants,
     * avoiding N+1 database and HTTP round-trips.
     */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<UUID> taskIds    = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        Set<UUID> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Set<UUID> phaseIds   = tasks.stream().map(Task::getPhaseId).collect(Collectors.toSet());

        //TODO: async
        Map<UUID, TaskProjectResponse> projectsById = projectService.findAllByIds(projectIds)
                .stream()
                .collect(Collectors.toMap(TaskProject::getId, projectService::toResponse));

        //TODO: async
        Map<UUID, TaskPhaseResponse> phasesById = phaseService.findAllByIds(phaseIds)
                .stream()
                .collect(Collectors.toMap(TaskPhase::getId, phaseService::toResponse));

        //TODO async
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

    //TODO is used anywhere? If not, delete
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

        //TODO async
        Map<UUID, TaskProject> projectsById = projectService.findAllByIds(projectIds).stream()
                .collect(Collectors.toMap(TaskProject::getId, p -> p));

        //TODO async
        Map<UUID, TaskPhase> phasesById = phaseService.findAllByIds(phaseIds).stream()
                .collect(Collectors.toMap(TaskPhase::getId, p -> p));

        //TODO async
        Map<UUID, String> userNamesById = userIds.isEmpty() ? Map.of() :
                userClientHelper.fetchUserNames(userIds);

        return tasks.stream().map(task -> {
            TaskProject project = projectsById.get(task.getProjectId());
            TaskPhase phase = phasesById.get(task.getPhaseId());
            //TODO use builder pattern
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

package com.demo.task.service;

import com.demo.common.dto.PageResponse;
import com.demo.common.dto.TaskBookedWorkResponse;
import com.demo.common.dto.TaskCommentRequest;
import com.demo.common.dto.TaskCommentResponse;
import com.demo.common.dto.TaskCompletionStatus;
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
import com.demo.common.dto.TimelineState;
import com.demo.common.dto.TaskSummaryResponse;
import com.demo.common.dto.TaskTimelineResponse;
import com.demo.common.dto.UserDto;
import com.demo.common.event.TaskChangedEvent;
import com.demo.common.event.TaskEvent;
import com.demo.common.exception.BusinessLogicException;
import com.demo.common.exception.RelatedEntityActiveException;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.client.UserClientHelper;
import com.demo.task.config.CacheConfig;
import com.demo.task.model.OutboxEventType;
import com.demo.task.model.Task;
import com.demo.task.model.TaskCodeJob;
import com.demo.task.model.TaskComment;
import com.demo.task.model.TaskPhase;
import com.demo.task.model.TaskProject;
import com.demo.task.outbox.OutboxWriter;
import com.demo.task.repository.TaskCodeJobRepository;
import com.demo.task.repository.TaskCommentRepository;
import com.demo.task.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

@Service
public class TaskService {

    private final TaskRepository repository;
    private final TaskCommentRepository commentRepository;
    private final UserClient userClient;
    private final UserClientHelper userClientHelper;
    private final TaskProjectService projectService;
    private final TaskPhaseService phaseService;
    private final TaskParticipantService participantService;
    private final TaskTimelineService timelineService;
    private final TaskPlannedWorkService plannedWorkService;
    private final TaskBookedWorkService bookedWorkService;
    private final OutboxWriter outboxWriter;
    private final TaskCodeJobRepository taskCodeJobRepository;

    public TaskService(TaskRepository repository,
                       TaskCommentRepository commentRepository,
                       UserClient userClient,
                       UserClientHelper userClientHelper,
                       TaskProjectService projectService,
                       TaskPhaseService phaseService,
                       TaskParticipantService participantService,
                       TaskTimelineService timelineService,
                       TaskPlannedWorkService plannedWorkService,
                       TaskBookedWorkService bookedWorkService,
                       OutboxWriter outboxWriter,
                       TaskCodeJobRepository taskCodeJobRepository) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.userClient = userClient;
        this.userClientHelper = userClientHelper;
        this.projectService = projectService;
        this.phaseService = phaseService;
        this.participantService = participantService;
        this.timelineService = timelineService;
        this.plannedWorkService = plannedWorkService;
        this.bookedWorkService = bookedWorkService;
        this.outboxWriter = outboxWriter;
        this.taskCodeJobRepository = taskCodeJobRepository;
    }

    /**
     * Returns a paginated summary page of tasks. When {@code includeFinished} is {@code false},
     * tasks in RELEASED or REJECTED phases are excluded from the result.
     * Falls back to returning all tasks when no finished phases exist yet (empty database).
     */
    public PageResponse<TaskSummaryResponse> findAll(boolean includeFinished, Pageable pageable) {
        List<UUID> finishedPhaseIds = resolveFinishedPhaseIds();
        Page<Task> page = (includeFinished || finishedPhaseIds.isEmpty())
                ? repository.findAll(pageable)
                : repository.findByPhaseIdNotIn(finishedPhaseIds, pageable);
        return toSummaryPageResponse(page);
    }

    /**
     * Returns the task with the given ID, caching the assembled response in Redis.
     * Evicted on any mutation (update, phase change, delete, participant change).
     */
    @Cacheable(value = CacheConfig.TASKS, key = "#id")
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
        // DelegatingSecurityContextExecutor captures the current SecurityContext from the request
        // thread and restores it on each ForkJoinPool worker, ensuring FeignAuthInterceptor can
        // extract the JWT token on the async threads (prevents unauthenticated Feign calls that
        // would trip the userService circuit breaker).
        Executor executor = new DelegatingSecurityContextExecutor(ForkJoinPool.commonPool());
        CompletableFuture<List<TaskTimelineResponse>> timelinesFuture =
                CompletableFuture.supplyAsync(() -> timelineService.findByTaskId(id), executor)
                        .orTimeout(10, TimeUnit.SECONDS);
        CompletableFuture<List<TaskPlannedWorkResponse>> plannedWorkFuture =
                CompletableFuture.supplyAsync(() -> plannedWorkService.findByTaskId(id), executor)
                        .orTimeout(10, TimeUnit.SECONDS);
        CompletableFuture<List<TaskBookedWorkResponse>> bookedWorkFuture =
                CompletableFuture.supplyAsync(() -> bookedWorkService.findByTaskId(id), executor)
                        .orTimeout(10, TimeUnit.SECONDS);

        // Fetch synchronous data while the async calls are running.
        TaskBaseData base = fetchBaseData(task);
        // Fetch full user profile; null when no user is assigned or user-service is unavailable.
        UserDto assignedUser = userClientHelper.fetchUser(task.getAssignedUserId());
        return new TaskFullResponse(
                task.getId(), task.getTaskCode(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getType(), task.getProgress(),
                base.participants(), base.project(), base.phase(), assignedUser,
                timelinesFuture.join(), plannedWorkFuture.join(), bookedWorkFuture.join(),
                task.getVersion());
    }

    /**
     * Returns a paginated summary page of tasks assigned to the specified user.
     * When {@code includeFinished} is {@code false}, RELEASED and REJECTED tasks are excluded.
     * Falls back to unfiltered when no finished phases exist yet (empty database).
     */
    public PageResponse<TaskSummaryResponse> findByUser(UUID userId, boolean includeFinished, Pageable pageable) {
        List<UUID> finishedPhaseIds = resolveFinishedPhaseIds();
        Page<Task> page = (includeFinished || finishedPhaseIds.isEmpty())
                ? repository.findByAssignedUserId(userId, pageable)
                : repository.findByAssignedUserIdAndPhaseIdNotIn(userId, finishedPhaseIds, pageable);
        return toSummaryPageResponse(page);
    }

    /**
     * Returns a paginated summary page of tasks whose status matches the given value (case-insensitive).
     * When {@code includeFinished} is {@code false}, RELEASED and REJECTED tasks are excluded.
     * Falls back to unfiltered when no finished phases exist yet (empty database).
     *
     * @param status string representation of {@link TaskStatus}
     */
    public PageResponse<TaskSummaryResponse> findByStatus(String status, boolean includeFinished, Pageable pageable) {
        TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
        List<UUID> finishedPhaseIds = resolveFinishedPhaseIds();
        Page<Task> page = (includeFinished || finishedPhaseIds.isEmpty())
                ? repository.findByStatus(taskStatus, pageable)
                : repository.findByStatusAndPhaseIdNotIn(taskStatus, finishedPhaseIds, pageable);
        return toSummaryPageResponse(page);
    }

    /**
     * Returns a paginated summary page of tasks belonging to the specified project.
     * When {@code includeFinished} is {@code false}, RELEASED and REJECTED tasks are excluded.
     * Falls back to unfiltered when no finished phases exist yet (empty database).
     */
    public PageResponse<TaskSummaryResponse> findByProject(UUID projectId, boolean includeFinished, Pageable pageable) {
        projectService.getOrThrow(projectId);
        List<UUID> finishedPhaseIds = resolveFinishedPhaseIds();
        Page<Task> page = (includeFinished || finishedPhaseIds.isEmpty())
                ? repository.findByProjectId(projectId, pageable)
                : repository.findByProjectIdAndPhaseIdNotIn(projectId, finishedPhaseIds, pageable);
        return toSummaryPageResponse(page);
    }

    /** Resolves the IDs of all RELEASED and REJECTED phases across all projects. */
    private List<UUID> resolveFinishedPhaseIds() {
        return phaseService.findIdsByNameIn(TaskPhaseName.FINISHED_PHASES);
    }

    /**
     * Returns a paginated summary page of tasks filtered by completion status.
     * {@code FINISHED} returns tasks in RELEASED or REJECTED phase; {@code DEV_FINISHED} returns tasks in DONE phase.
     * Returns an empty page when no matching phases exist yet (empty database).
     */
    public PageResponse<TaskSummaryResponse> findByCompletionStatus(TaskCompletionStatus status, Pageable pageable) {
        Set<TaskPhaseName> phaseNames = status == TaskCompletionStatus.FINISHED
                ? TaskPhaseName.FINISHED_PHASES
                : Set.of(TaskPhaseName.DONE);
        List<UUID> phaseIds = phaseService.findIdsByNameIn(phaseNames);
        if (phaseIds.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
        }
        return toSummaryPageResponse(repository.findByPhaseIdIn(phaseIds, pageable));
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
        // fetchUser is cached (Caffeine, 10 min TTL) so repeated calls for the same user
        // are served from memory — user-service is only hit once per unique user ID.
        UserDto user = userClientHelper.fetchUser(request.getAssignedUserId());
        if (user == null) {
            throw new ResourceNotFoundException("User", request.getAssignedUserId());
        }

        TaskProject project = projectService.getOrThrow(request.getProjectId());

        // Every new task starts in the PLANNING phase; any phaseId in the request is ignored.
        UUID phaseId = phaseService.findPlanningPhaseOrThrow(project.getId()).getId();

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .type(request.getType())
                .progress(request.getProgress())
                .assignedUserId(request.getAssignedUserId())
                .projectId(request.getProjectId())
                .phaseId(phaseId)
                // taskCode is intentionally null here; the background TaskCodeAssignmentService
                // will assign it within ~1 second and push a TASK_CODE_ASSIGNED event to the frontend.
                .build();
        Task saved = repository.save(task);

        // Insert a job row atomically with the task so the scheduler never misses an assignment,
        // even if the application crashes immediately after saving the task.
        taskCodeJobRepository.save(TaskCodeJob.builder()
                .taskId(saved.getId())
                .projectId(saved.getProjectId())
                .createdAt(Instant.now())
                .build());

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
    @CacheEvict(value = CacheConfig.TASKS, key = "#id")
    public TaskResponse update(UUID id, TaskRequest request) {
        // PESSIMISTIC_WRITE (SELECT FOR UPDATE) serialises concurrent writes on the same task.
        // After the previous holder commits, the next waiter re-reads the committed version, so
        // the version check below correctly rejects clients that sent a now-stale version number.
        Task task = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
        validateFieldsEditable(phaseService.getOrThrow(task.getPhaseId()).getName());

        TaskStatus oldStatus = task.getStatus();

        if (!task.getProjectId().equals(request.getProjectId())) {
            projectService.getOrThrow(request.getProjectId());
        }

        // If the client supplied a version, reject immediately when it doesn't match the DB value.
        // This covers the deterministic stale-write case (client re-uses an old version number).
        // The concurrent case (N clients all read version=0 and submit simultaneously) is handled
        // by Hibernate's @Version: exactly one UPDATE WHERE version=0 wins; the rest get
        // ObjectOptimisticLockingFailureException at commit time, mapped to HTTP 409 by
        // OptimisticLockExceptionHandler.
        if (request.getVersion() != null && !request.getVersion().equals(task.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Task.class, id);
        }
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setType(request.getType());
        task.setProgress(request.getProgress());
        task.setAssignedUserId(request.getAssignedUserId());
        task.setProjectId(request.getProjectId());
        // saveAndFlush forces the UPDATE (with WHERE version=?) to execute immediately inside the
        // JPA repository proxy, where Spring's exception translation converts StaleObjectStateException
        // → ObjectOptimisticLockingFailureException → HTTP 409. Without flush, the UPDATE runs at
        // commit time and the exception may surface as TransactionSystemException (unmapped → HTTP 500).
        Task saved = repository.saveAndFlush(task);

        participantService.setAssignee(saved.getId(), request.getAssignedUserId());
        publishStatusChangedEvent(saved, oldStatus, request.getStatus());
        outboxWriter.write(TaskChangedEvent.taskUpdated(saved.getId(), saved.getAssignedUserId(),
                saved.getProjectId(), saved.getTitle()));

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
    @CacheEvict(value = CacheConfig.TASKS, key = "#id")
    public TaskResponse updatePhase(UUID id, UUID phaseId) {
        Task task = getOrThrow(id);
        UUID oldPhaseId = task.getPhaseId();

        TaskPhase currentPhase = phaseService.getOrThrow(oldPhaseId);
        TaskPhase newPhase     = phaseService.getOrThrow(phaseId);

        // Fully finished tasks are immutable — no phase changes allowed.
        validateNotFinished(currentPhase.getName());

        // One-way gate: once a task has left PLANNING it may never return.
        if (currentPhase.getName() != TaskPhaseName.PLANNING && newPhase.getName() == TaskPhaseName.PLANNING) {
            throw new IllegalArgumentException("Cannot return a task to the PLANNING phase");
        }

        task.setPhaseId(phaseId);
        // Record when the task becomes "closed" so the archive scheduler can enforce the TTL window.
        if (TaskPhaseName.FINISHED_PHASES.contains(newPhase.getName()) && task.getClosedAt() == null) {
            task.setClosedAt(Instant.now());
        }
        Task saved = repository.save(task);

        recordAutomaticTimelines(saved, currentPhase.getName(), newPhase.getName());

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

    /**
     * Records REAL_START, REAL_END, and RELEASE_DATE timeline entries automatically
     * based on the phase transition. Called within the updatePhase transaction.
     */
    private void recordAutomaticTimelines(Task task, TaskPhaseName fromPhase, TaskPhaseName toPhase) {
        UUID assignee = task.getAssignedUserId();
        // Record REAL_START once — the first time the task leaves PLANNING
        if (fromPhase == TaskPhaseName.PLANNING) {
            timelineService.setAutomaticIfAbsent(task.getId(), TimelineState.REAL_START, assignee);
        }
        // Record REAL_END whenever the task enters a terminal phase (DONE, RELEASED, REJECTED)
        if (TaskPhaseName.FIELD_LOCKED_PHASES.contains(toPhase)) {
            timelineService.upsertAutomatic(task.getId(), TimelineState.REAL_END, assignee);
        }
        // Record RELEASE_DATE when the task enters RELEASED
        if (toPhase == TaskPhaseName.RELEASED) {
            timelineService.upsertAutomatic(task.getId(), TimelineState.RELEASE_DATE, assignee);
        }
    }

    /** Throws {@link BusinessLogicException} if the phase is RELEASED or REJECTED (fully finished). */
    private void validateNotFinished(TaskPhaseName phaseName) {
        if (TaskPhaseName.FINISHED_PHASES.contains(phaseName)) {
            throw new BusinessLogicException("Task is finished and cannot be modified");
        }
    }

    /** Throws {@link BusinessLogicException} if the phase is DONE, RELEASED, or REJECTED (fields locked). */
    private void validateFieldsEditable(TaskPhaseName phaseName) {
        if (TaskPhaseName.FIELD_LOCKED_PHASES.contains(phaseName)) {
            throw new BusinessLogicException("Task fields are locked in the " + phaseName + " phase");
        }
    }

    /** Publishes a STATUS_CHANGED outbox event when the status has actually changed. */
    private void publishStatusChangedEvent(Task saved, TaskStatus oldStatus, TaskStatus newStatus) {
        if (newStatus == null || newStatus.equals(oldStatus)) return;
        outboxWriter.write(TaskChangedEvent.statusChanged(saved.getId(), saved.getAssignedUserId(),
                saved.getProjectId(), saved.getTitle(), oldStatus, newStatus));
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
        validateNotFinished(phaseService.getOrThrow(task.getPhaseId()).getName());
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
        if (phaseName != TaskPhaseName.PLANNING) {
            throw new IllegalArgumentException("Planned dates can only be changed while the task is in the PLANNING phase");
        }
        timelineService.updatePlannedDates(taskId, request.getPlannedStart(), request.getPlannedEnd(), updatingUserId);

        // Republish a lifecycle event so downstream projections (search, reporting)
        // pick up the new planned dates.
        TaskProject project = projectService.getOrThrow(task.getProjectId());
        String userName     = userClientHelper.resolveUserName(task.getAssignedUserId());
        writeTaskLifecycleOutboxEvent(task, OutboxEventType.TASK_UPDATED,
                project.getName(), task.getPhaseId(), userName);

        return findFullById(taskId);
    }

    /** Soft-deletes the task; throws if the task has any associated comments. */
    @Transactional
    @CacheEvict(value = CacheConfig.TASKS, key = "#id")
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
        Instant plannedStart = timelineService.findPlannedStart(task.getId());
        Instant plannedEnd   = timelineService.findPlannedEnd(task.getId());
        TaskEvent event = switch (eventType) {
            case TASK_CREATED -> TaskEvent.created(task.getId(), task.getTaskCode(),
                    task.getTitle(), task.getDescription(),
                    task.getStatus(), task.getProjectId(), projectName,
                    phaseId, phaseName, task.getAssignedUserId(), userName,
                    plannedStart, plannedEnd);
            case TASK_UPDATED -> TaskEvent.updated(task.getId(), task.getTaskCode(),
                    task.getTitle(), task.getDescription(),
                    task.getStatus(), task.getProjectId(), projectName,
                    phaseId, phaseName, task.getAssignedUserId(), userName,
                    plannedStart, plannedEnd);
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
        outboxWriter.writeTaskEvent(taskId, eventType, event);
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
                base.participants(), base.project(), base.phase(), task.getVersion());
    }

    /** Fetches participants, project, and phase for a single task — shared by toResponse and findFullById. */
    private TaskBaseData fetchBaseData(Task task) {
        TaskProject project = projectService.getOrThrow(task.getProjectId());
        TaskPhase phase = phaseService.getOrThrow(task.getPhaseId());
        return new TaskBaseData(
                participantService.findByTaskId(task.getId()),
                new TaskProjectResponse(project.getId(), project.getName(), project.getDescription(),
                        project.getTaskCodePrefix(), project.getDefaultPhaseId()),
                new TaskPhaseResponse(phase.getId(), phase.getName(), phase.getDescription(),
                        phase.getCustomName(), phase.getProjectId()));
    }

    /** Bundles the three sub-fetches that are common to both the standard and full task responses. */
    private record TaskBaseData(List<TaskParticipantResponse> participants,
                                TaskProjectResponse project,
                                TaskPhaseResponse phase) {}

    /**
     * Converts a list of tasks to response DTOs using batch queries for projects, phases, and participants,
     * avoiding N+1 database and HTTP round-trips.
     * All three fetches are DB calls sharing the same HikariCP pool, so they run sequentially
     * to avoid multiplying connection demand under concurrent load.
     */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<UUID> taskIds    = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        Set<UUID> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Set<UUID> phaseIds   = tasks.stream().map(Task::getPhaseId).collect(Collectors.toSet());

        Map<UUID, TaskProjectResponse> projectsById = projectService.findAllByIds(projectIds)
                .stream()
                .collect(Collectors.toMap(TaskProject::getId, p ->
                        new TaskProjectResponse(p.getId(), p.getName(), p.getDescription(),
                                p.getTaskCodePrefix(), p.getDefaultPhaseId())));

        Map<UUID, TaskPhaseResponse> phasesById = phaseService.findAllByIds(phaseIds)
                .stream()
                .collect(Collectors.toMap(TaskPhase::getId, p ->
                        new TaskPhaseResponse(p.getId(), p.getName(), p.getDescription(),
                                p.getCustomName(), p.getProjectId())));

        // Batch-load all participants for these tasks in two queries (DB + user-service batch)
        Map<UUID, List<TaskParticipantResponse>> participantsByTaskId =
                participantService.findByTaskIds(taskIds);

        return tasks.stream().map(task -> new TaskResponse(
                task.getId(), task.getTaskCode(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getType(), task.getProgress(),
                participantsByTaskId.getOrDefault(task.getId(), List.of()),
                projectsById.get(task.getProjectId()),
                phasesById.get(task.getPhaseId()),
                task.getVersion()))
                .toList();
    }

    /**
     * Converts a list of tasks to lightweight summary DTOs using batch queries for projects,
     * phases, and user names — no per-task participant loading.
     * DB calls (projects, phases) run sequentially to avoid multiplying connection demand.
     * User-name resolution (Redis/HTTP) is fired async first so it overlaps with the DB queries.
     */
    private List<TaskSummaryResponse> toSummaryResponseList(List<Task> tasks) {
        if (tasks.isEmpty()) return List.of();

        Set<UUID> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Set<UUID> phaseIds   = tasks.stream().map(Task::getPhaseId).collect(Collectors.toSet());
        Set<UUID> userIds    = tasks.stream().map(Task::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet());

        // Fire user-name resolution async first (Redis/HTTP — no DB connection consumed).
        // The result is joined after the DB queries complete, so the two I/O paths overlap.
        // DelegatingSecurityContextExecutor propagates the calling thread's SecurityContext so
        // FeignAuthInterceptor can reconstruct the Bearer header on the ForkJoinPool worker thread.
        Executor summaryExecutor = new DelegatingSecurityContextExecutor(ForkJoinPool.commonPool());
        CompletableFuture<Map<UUID, String>> userNamesFuture = CompletableFuture.supplyAsync(() ->
                userIds.isEmpty() ? Map.of() : userClientHelper.fetchUserNames(userIds), summaryExecutor);

        Map<UUID, TaskProject> projectsById = projectService.findAllByIds(projectIds).stream()
                .collect(Collectors.toMap(TaskProject::getId, p -> p));

        Map<UUID, TaskPhase> phasesById = phaseService.findAllByIds(phaseIds).stream()
                .collect(Collectors.toMap(TaskPhase::getId, p -> p));

        Map<UUID, String> userNamesById = userNamesFuture.join();

        return tasks.stream().map(task -> {
            TaskProject project = projectsById.get(task.getProjectId());
            TaskPhase phase = phasesById.get(task.getPhaseId());
            return TaskSummaryResponse.builder()
                    .id(task.getId())
                    .taskCode(task.getTaskCode())
                    .title(task.getTitle())
                    .description(task.getDescription())
                    .status(task.getStatus())
                    .type(task.getType())
                    .progress(task.getProgress())
                    .assignedUserId(task.getAssignedUserId())
                    .assignedUserName(userNamesById.get(task.getAssignedUserId()))
                    .projectId(project != null ? project.getId() : null)
                    .projectName(project != null ? project.getName() : null)
                    .phaseId(phase != null ? phase.getId() : null)
                    // getName() returns TaskPhaseName enum; .name() converts to String for the response
                    .phaseName(phase != null ? phase.getName().name() : null)
                    .build();
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

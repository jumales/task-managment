package com.demo.task.service;

import com.demo.common.dto.TaskTimelineRequest;
import com.demo.common.dto.TaskTimelineResponse;
import com.demo.common.dto.TimelineState;
import com.demo.common.dto.UserDto;
import com.demo.common.exception.ResourceNotFoundException;
import com.demo.task.client.UserClient;
import com.demo.task.client.UserClientHelper;
import com.demo.task.model.Task;
import com.demo.task.model.TaskTimeline;
import com.demo.task.repository.TaskRepository;
import com.demo.task.repository.TaskTimelineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages timeline entries for tasks, tracking planned and actual start/end dates per state.
 * Setting a state is an upsert: if an active entry already exists for that state, it is updated.
 * Enforces that start timestamps are always strictly before their corresponding end timestamps.
 */
@Service
public class TaskTimelineService {

    /** Maps each state to its ordering counterpart; used to enforce start &lt; end invariants. */
    private static final Map<TimelineState, TimelineState> ORDERING_COUNTERPARTS = Map.of(
            TimelineState.PLANNED_START, TimelineState.PLANNED_END,
            TimelineState.PLANNED_END,   TimelineState.PLANNED_START,
            TimelineState.REAL_START,    TimelineState.REAL_END,
            TimelineState.REAL_END,      TimelineState.REAL_START
    );

    private final TaskTimelineRepository repository;
    private final TaskRepository taskRepository;
    private final UserClient userClient;
    private final UserClientHelper userClientHelper;

    public TaskTimelineService(TaskTimelineRepository repository,
                               TaskRepository taskRepository,
                               UserClient userClient,
                               UserClientHelper userClientHelper) {
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.userClient = userClient;
        this.userClientHelper = userClientHelper;
    }

    /** Returns all active timeline entries for the given task, enriched with user display names. */
    public List<TaskTimelineResponse> findByTaskId(UUID taskId) {
        getTaskOrThrow(taskId);
        List<TaskTimeline> entries = repository.findByTaskIdOrderByStateAsc(taskId);
        return toResponseList(entries);
    }

    /**
     * Sets a timeline state on the task (upsert).
     * If an active entry already exists for this state, its timestamp and set-by user are updated.
     * If not, a new entry is created.
     * Validates that the task and the set-by user both exist, and that start &lt; end ordering is preserved.
     */
    @Transactional
    public TaskTimelineResponse setState(UUID taskId, TimelineState state, TaskTimelineRequest request) {
        getTaskOrThrow(taskId);
        UserDto user = userClient.getUserById(request.getSetByUserId());
        validateOrdering(taskId, state, request.getTimestamp());

        Optional<TaskTimeline> existing = repository.findByTaskIdAndState(taskId, state);
        TaskTimeline entry = existing.map(e -> updateEntry(e, request))
                .orElseGet(() -> createEntry(taskId, state, request));

        TaskTimeline saved = repository.save(entry);
        return toResponse(saved, user.getName());
    }

    /**
     * Removes the active timeline entry for the given state on a task.
     * Throws if no active entry exists for that state.
     */
    @Transactional
    public void deleteState(UUID taskId, TimelineState state) {
        getTaskOrThrow(taskId);
        TaskTimeline entry = repository.findByTaskIdAndState(taskId, state)
                .orElseThrow(() -> new ResourceNotFoundException("TaskTimeline state " + state + " on task", taskId));
        repository.deleteById(entry.getId());
    }

    /**
     * Creates the mandatory PLANNED_START and PLANNED_END timeline entries for a newly created task.
     * Called by {@link TaskService} immediately after the task is persisted.
     * Does not re-validate the task existence since it is invoked within the task creation transaction.
     */
    @Transactional
    void createInitialTimelines(UUID taskId, Instant plannedStart, Instant plannedEnd, UUID creatorId) {
        repository.saveAll(List.of(
                buildEntry(taskId, TimelineState.PLANNED_START, plannedStart, creatorId),
                buildEntry(taskId, TimelineState.PLANNED_END, plannedEnd, creatorId)));
    }

    /**
     * Updates both PLANNED_START and PLANNED_END for the task in a single SQL statement.
     * Validates that plannedStart is strictly before plannedEnd before writing.
     * Package-private for use by {@link TaskService}.
     */
    @Transactional
    void updatePlannedDates(UUID taskId, Instant plannedStart, Instant plannedEnd, UUID updatingUserId) {
        if (!plannedStart.isBefore(plannedEnd)) {
            throw new IllegalArgumentException("plannedStart must be before plannedEnd");
        }
        repository.updatePlannedTimestamps(taskId, plannedStart, plannedEnd, updatingUserId);
    }

    /**
     * Validates that the new timestamp preserves the start &lt; end ordering invariant.
     * Checks the counterpart state (e.g. PLANNED_END when setting PLANNED_START) if it already exists.
     */
    private void validateOrdering(UUID taskId, TimelineState state, Instant newTimestamp) {
        TimelineState counterpart = ORDERING_COUNTERPARTS.get(state);
        repository.findByTaskIdAndState(taskId, counterpart).ifPresent(existing -> {
            boolean settingStart = state == TimelineState.PLANNED_START || state == TimelineState.REAL_START;
            if (settingStart && !newTimestamp.isBefore(existing.getTimestamp())) {
                throw new IllegalArgumentException(
                        state + " must be before " + counterpart + " (" + existing.getTimestamp() + ")");
            }
            if (!settingStart && !existing.getTimestamp().isBefore(newTimestamp)) {
                throw new IllegalArgumentException(
                        counterpart + " must be before " + state + " (" + newTimestamp + ")");
            }
        });
    }

    private Task getTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private TaskTimeline createEntry(UUID taskId, TimelineState state, TaskTimelineRequest request) {
        return buildEntry(taskId, state, request.getTimestamp(), request.getSetByUserId());
    }

    private TaskTimeline buildEntry(UUID taskId, TimelineState state, Instant timestamp, UUID setByUserId) {
        return TaskTimeline.builder()
                .taskId(taskId)
                .state(state)
                .timestamp(timestamp)
                .setByUserId(setByUserId)
                .createdAt(Instant.now())
                .build();
    }

    private TaskTimeline updateEntry(TaskTimeline entry, TaskTimelineRequest request) {
        entry.setTimestamp(request.getTimestamp());
        entry.setSetByUserId(request.getSetByUserId());
        return entry;
    }

    /** Enriches a list of timeline entries with user display names, resolved and cached per user ID. */
    private List<TaskTimelineResponse> toResponseList(List<TaskTimeline> entries) {
        return entries.stream()
                .map(e -> toResponse(e, userClientHelper.resolveUserName(e.getSetByUserId())))
                .toList();
    }

    private TaskTimelineResponse toResponse(TaskTimeline entry, String userName) {
        return new TaskTimelineResponse(
                entry.getId(),
                entry.getState(),
                entry.getTimestamp(),
                entry.getSetByUserId(),
                userName,
                entry.getCreatedAt());
    }
}

package com.demo.task.controller;

import com.demo.common.dto.TaskTimelineRequest;
import com.demo.common.dto.TaskTimelineResponse;
import com.demo.common.dto.TimelineState;
import com.demo.task.service.TaskTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Endpoints for managing timeline entries (planned/actual start and end dates) on a task.
 * REAL_START, REAL_END, and RELEASE_DATE are system-managed and cannot be set or cleared manually.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/timelines")
@Tag(name = "Task Timelines", description = "Track planned and actual start/end dates per task")
public class TaskTimelineController {

    /** Timeline states that are set automatically by phase transitions and must not be user-editable. */
    private static final Set<TimelineState> AUTO_STATES =
            Set.of(TimelineState.REAL_START, TimelineState.REAL_END, TimelineState.RELEASE_DATE);

    private final TaskTimelineService service;

    public TaskTimelineController(TaskTimelineService service) {
        this.service = service;
    }

    /** Returns all active timeline entries for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List timeline entries for a task")
    public List<TaskTimelineResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /**
     * Sets a timeline state on the task; creates or updates the entry for that state.
     * Rejects requests for auto-managed states (REAL_START, REAL_END, RELEASE_DATE).
     */
    @PutMapping("/{state}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set a timeline state on a task (upsert)",
               description = "REAL_START, REAL_END, and RELEASE_DATE are managed automatically and will be rejected.")
    public TaskTimelineResponse setState(@PathVariable UUID taskId,
                                         @PathVariable TimelineState state,
                                         @RequestBody TaskTimelineRequest request) {
        if (AUTO_STATES.contains(state)) {
            throw new IllegalArgumentException(state + " is managed automatically and cannot be set manually");
        }
        return service.setState(taskId, state, request);
    }

    /**
     * Removes the active timeline entry for the given state.
     * Rejects requests for auto-managed states (REAL_START, REAL_END, RELEASE_DATE).
     */
    @DeleteMapping("/{state}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a timeline state from a task",
               description = "REAL_START, REAL_END, and RELEASE_DATE are managed automatically and will be rejected.")
    public void deleteState(@PathVariable UUID taskId, @PathVariable TimelineState state) {
        if (AUTO_STATES.contains(state)) {
            throw new IllegalArgumentException(state + " is managed automatically and cannot be cleared manually");
        }
        service.deleteState(taskId, state);
    }
}

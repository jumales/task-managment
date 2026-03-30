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
import java.util.UUID;

/**
 * Endpoints for managing timeline entries (planned/actual start and end dates) on a task.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/timelines")
@Tag(name = "Task Timelines", description = "Track planned and actual start/end dates per task")
public class TaskTimelineController {

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

    /** Sets a timeline state on the task; creates or updates the entry for that state. */
    @PutMapping("/{state}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set a timeline state on a task (upsert)")
    public TaskTimelineResponse setState(@PathVariable UUID taskId,
                                         @PathVariable TimelineState state,
                                         @RequestBody TaskTimelineRequest request) {
        return service.setState(taskId, state, request);
    }

    /** Removes the active timeline entry for the given state. */
    @DeleteMapping("/{state}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a timeline state from a task")
    public void deleteState(@PathVariable UUID taskId, @PathVariable TimelineState state) {
        service.deleteState(taskId, state);
    }
}

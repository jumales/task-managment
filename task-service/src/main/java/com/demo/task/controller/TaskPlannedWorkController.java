package com.demo.task.controller;

import com.demo.common.dto.TaskPlannedWorkRequest;
import com.demo.common.dto.TaskPlannedWorkResponse;
import com.demo.task.service.TaskPlannedWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing planned-work entries (estimated hours per work type) on a task.
 * Entries are immutable once created and restricted to the planning phase (task status TODO).
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/planned-work")
@Tag(name = "Task Planned Work", description = "Track estimated hours per work type on a task during planning phase")
public class TaskPlannedWorkController {

    private final TaskPlannedWorkService service;

    public TaskPlannedWorkController(TaskPlannedWorkService service) {
        this.service = service;
    }

    /** Returns all planned-work entries for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List planned-work entries for a task")
    public List<TaskPlannedWorkResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /** Creates a new planned-work entry on the task. Only allowed when task status is TODO. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a planned-work entry to a task",
               description = "Allowed only while the task status is TODO (planning phase). One entry per work type per task.")
    public TaskPlannedWorkResponse create(@PathVariable UUID taskId,
                                          @RequestBody TaskPlannedWorkRequest request) {
        return service.create(taskId, request);
    }
}

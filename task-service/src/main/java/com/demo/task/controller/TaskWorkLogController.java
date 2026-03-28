package com.demo.task.controller;

import com.demo.common.dto.TaskWorkLogRequest;
import com.demo.common.dto.TaskWorkLogResponse;
import com.demo.task.service.TaskWorkLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing work log entries (planned and booked hours) on a task.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/work-logs")
@Tag(name = "Task Work Logs", description = "Track planned and booked hours per user and work type on a task")
public class TaskWorkLogController {

    private final TaskWorkLogService service;

    public TaskWorkLogController(TaskWorkLogService service) {
        this.service = service;
    }

    /** Returns all work log entries for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List work logs for a task")
    public List<TaskWorkLogResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /** Creates a new work log entry on the task. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a work log entry to a task")
    public TaskWorkLogResponse create(@PathVariable UUID taskId,
                                      @RequestBody TaskWorkLogRequest request) {
        return service.create(taskId, request);
    }

    /** Updates an existing work log entry. */
    @PutMapping("/{workLogId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a work log entry")
    public TaskWorkLogResponse update(@PathVariable UUID taskId,
                                      @PathVariable UUID workLogId,
                                      @RequestBody TaskWorkLogRequest request) {
        return service.update(taskId, workLogId, request);
    }

    /** Soft-deletes a work log entry. */
    @DeleteMapping("/{workLogId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a work log entry")
    public void delete(@PathVariable UUID taskId, @PathVariable UUID workLogId) {
        service.delete(taskId, workLogId);
    }
}

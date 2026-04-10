package com.demo.task.controller;

import com.demo.common.dto.TaskBookedWorkRequest;
import com.demo.common.dto.TaskBookedWorkResponse;
import com.demo.task.client.UserClientHelper;
import com.demo.task.service.TaskBookedWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing booked-work entries (actual hours worked per user and work type) on a task.
 * Multiple entries are allowed per task and work type.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/booked-work")
@Tag(name = "Task Booked Work", description = "Track actual hours worked per user and work type on a task")
public class TaskBookedWorkController {

    private final TaskBookedWorkService service;
    private final UserClientHelper userClientHelper;

    public TaskBookedWorkController(TaskBookedWorkService service, UserClientHelper userClientHelper) {
        this.service = service;
        this.userClientHelper = userClientHelper;
    }

    /** Returns all active booked-work entries for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List booked-work entries for a task")
    public List<TaskBookedWorkResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /** Creates a new booked-work entry on the task, attributed to the authenticated user. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a booked-work entry to a task")
    public TaskBookedWorkResponse create(@PathVariable UUID taskId,
                                         @RequestBody TaskBookedWorkRequest request,
                                         Authentication authentication) {
        UUID userId = userClientHelper.resolveUserId(authentication);
        return service.create(taskId, userId, request);
    }

    /** Updates an existing booked-work entry, keeping the authenticated user as owner. */
    @PutMapping("/{bookedWorkId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update a booked-work entry")
    public TaskBookedWorkResponse update(@PathVariable UUID taskId,
                                         @PathVariable UUID bookedWorkId,
                                         @RequestBody TaskBookedWorkRequest request,
                                         Authentication authentication) {
        UUID userId = userClientHelper.resolveUserId(authentication);
        return service.update(taskId, bookedWorkId, userId, request);
    }

    /** Soft-deletes a booked-work entry. */
    @DeleteMapping("/{bookedWorkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a booked-work entry")
    public void delete(@PathVariable UUID taskId, @PathVariable UUID bookedWorkId) {
        service.delete(taskId, bookedWorkId);
    }
}

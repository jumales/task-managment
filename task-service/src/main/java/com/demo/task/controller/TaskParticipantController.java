package com.demo.task.controller;

import com.demo.common.dto.TaskParticipantRequest;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.task.service.TaskParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing participants (user-role associations) on a task.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/participants")
@Tag(name = "Task Participants", description = "Manage participants and their roles on a task")
public class TaskParticipantController {

    private final TaskParticipantService service;

    public TaskParticipantController(TaskParticipantService service) {
        this.service = service;
    }

    /** Returns all participants for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List task participants")
    public List<TaskParticipantResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /** Adds a participant with a role to the task. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a participant to a task")
    public TaskParticipantResponse add(@PathVariable UUID taskId,
                                       @RequestBody TaskParticipantRequest request) {
        return service.add(taskId, request);
    }

    /** Removes a participant from the task. */
    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a participant from a task")
    public void remove(@PathVariable UUID taskId, @PathVariable UUID participantId) {
        service.remove(participantId);
    }
}

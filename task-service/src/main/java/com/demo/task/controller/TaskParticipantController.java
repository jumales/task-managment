package com.demo.task.controller;

import com.demo.common.dto.TaskParticipantResponse;
import com.demo.task.client.UserClientHelper;
import com.demo.task.service.TaskParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing participants (user-role associations) on a task.
 * Participants are added automatically when users interact with the task (comment,
 * upload, book hours). The only manual action is Watch / Unwatch (WATCHER role).
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/participants")
@Tag(name = "Task Participants", description = "Manage participants and their roles on a task")
public class TaskParticipantController {

    private final TaskParticipantService service;
    private final UserClientHelper userClientHelper;

    public TaskParticipantController(TaskParticipantService service,
                                     UserClientHelper userClientHelper) {
        this.service = service;
        this.userClientHelper = userClientHelper;
    }

    /** Returns all participants for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List task participants")
    public List<TaskParticipantResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /**
     * Adds the authenticated user as a WATCHER on the task.
     * If the user already has any role on the task, returns that existing entry.
     */
    @PostMapping("/watch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Watch a task (adds authenticated user as WATCHER)")
    public TaskParticipantResponse watch(@PathVariable UUID taskId, Authentication authentication) {
        UUID userId = userClientHelper.resolveUserId(authentication);
        return service.watch(taskId, userId);
    }

    /**
     * Removes a WATCHER participant entry.
     * Only the participant themselves may remove their own WATCHER entry.
     */
    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Unwatch a task (removes own WATCHER entry)")
    public void remove(@PathVariable UUID taskId,
                       @PathVariable UUID participantId,
                       Authentication authentication) {
        UUID requestingUserId = userClientHelper.resolveUserId(authentication);
        service.remove(participantId, requestingUserId);
    }
}

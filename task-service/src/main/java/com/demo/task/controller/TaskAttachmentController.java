package com.demo.task.controller;

import com.demo.common.dto.TaskAttachmentRequest;
import com.demo.common.dto.TaskAttachmentResponse;
import com.demo.task.service.TaskAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints for managing file attachments on a task.
 * Files must be uploaded to file-service first; these endpoints register the metadata
 * and link the file to the task.
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/attachments")
@Tag(name = "Task Attachments", description = "Attach files to tasks and manage attachment metadata")
public class TaskAttachmentController {

    private final TaskAttachmentService service;

    public TaskAttachmentController(TaskAttachmentService service) {
        this.service = service;
    }

    /** Returns all attachments for the given task. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List attachments for a task")
    public List<TaskAttachmentResponse> getAll(@PathVariable UUID taskId) {
        return service.findByTaskId(taskId);
    }

    /**
     * Registers an uploaded file as a task attachment.
     * The file must already exist in file-service (upload via {@code POST /api/v1/files/attachments} first).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add an attachment to a task")
    public TaskAttachmentResponse create(@PathVariable UUID taskId,
                                         @RequestBody TaskAttachmentRequest request,
                                         Authentication authentication) {
        return service.create(taskId, request, authentication);
    }

    /**
     * Permanently deletes the attachment record and removes the file from storage.
     * The caller's token is forwarded to file-service to authorise the deletion.
     */
    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a task attachment")
    public void delete(@PathVariable UUID taskId,
                       @PathVariable UUID attachmentId,
                       Authentication authentication) {
        String bearerToken = extractBearerToken(authentication);
        service.delete(taskId, attachmentId, bearerToken);
    }

    /** Extracts the raw Bearer token string from the authentication context. */
    private String extractBearerToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return "Bearer " + jwt.getTokenValue();
        }
        // Non-JWT path (integration tests): no token to forward
        return null;
    }
}

package com.demo.task.controller;

import com.demo.common.dto.ProjectNotificationTemplateRequest;
import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.dto.TemplatePlaceholder;
import com.demo.common.event.TaskChangeType;
import com.demo.common.web.ResponseCode;
import com.demo.task.service.ProjectNotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Manages per-project email notification templates.
 * Templates override the default email content for a specific event type.
 * Any authenticated user may read or edit templates; the gateway enforces authentication.
 */
@Tag(name = "Project Notification Templates",
        description = "Configure per-project email templates for task-change event notifications.")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/notification-templates")
public class ProjectNotificationTemplateController {

    private final ProjectNotificationTemplateService service;

    public ProjectNotificationTemplateController(ProjectNotificationTemplateService service) {
        this.service = service;
    }

    /** Returns all available template placeholders that can be used in subject and body templates. */
    @Operation(summary = "List all supported template placeholders",
               description = "Returns every {placeholder} token that the notification engine will substitute at send time.")
    @ApiResponse(responseCode = ResponseCode.OK, description = "Placeholder catalogue returned")
    @GetMapping("/placeholders")
    public List<TemplatePlaceholder> getPlaceholders(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId) {
        return Arrays.asList(TemplatePlaceholder.values());
    }

    /** Returns all active notification templates configured for the project. */
    @Operation(summary = "List all notification templates for a project")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Templates returned"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Project not found")
    })
    @GetMapping
    public List<ProjectNotificationTemplateResponse> getAll(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId) {
        return service.findByProjectId(projectId);
    }

    /** Returns the notification template for the given event type. */
    @Operation(summary = "Get a notification template by event type")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Template found"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Template not found")
    })
    @GetMapping("/{eventType}")
    public ProjectNotificationTemplateResponse getByEventType(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Event type") @PathVariable TaskChangeType eventType) {
        return service.findByProjectIdAndEventType(projectId, eventType);
    }

    /** Creates or replaces the notification template for the given event type. */
    @Operation(summary = "Create or replace a notification template",
               description = "Accepts {placeholder} tokens listed by GET /placeholders. Unknown tokens are rejected with 400.")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.OK, description = "Template saved"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Project not found"),
            @ApiResponse(responseCode = ResponseCode.BAD_REQUEST, description = "Unknown placeholder token in template")
    })
    @PutMapping("/{eventType}")
    public ProjectNotificationTemplateResponse upsert(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Event type") @PathVariable TaskChangeType eventType,
            @RequestBody ProjectNotificationTemplateRequest request) {
        return service.upsert(projectId, eventType, request);
    }

    /** Soft-deletes the notification template for the given event type. */
    @Operation(summary = "Delete a notification template")
    @ApiResponses({
            @ApiResponse(responseCode = ResponseCode.NO_CONTENT, description = "Template deleted"),
            @ApiResponse(responseCode = ResponseCode.NOT_FOUND, description = "Template not found")
    })
    @DeleteMapping("/{eventType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Project UUID") @PathVariable UUID projectId,
            @Parameter(description = "Event type") @PathVariable TaskChangeType eventType) {
        service.delete(projectId, eventType);
    }
}

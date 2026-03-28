package com.demo.notification.controller;

import com.demo.common.dto.PageResponse;
import com.demo.notification.dto.NotificationResponse;
import com.demo.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for querying email notification history.
 */
@Tag(name = "Notifications", description = "Query email notification history for tasks")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Returns a paginated page of email notifications sent for the given task, in chronological order. */
    @Operation(summary = "Get notification history for a task",
               description = "Returns a paginated page of email notifications sent for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<NotificationResponse> getByTaskId(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return notificationService.getByTaskId(taskId, pageable);
    }
}

package com.demo.notification.controller;

import com.demo.notification.dto.NotificationResponse;
import com.demo.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /** Returns all email notifications sent for the given task, in chronological order. */
    @Operation(summary = "Get notification history for a task",
               description = "Returns all email notifications sent for the given task, ordered chronologically.")
    @GetMapping("/tasks/{taskId}")
    public List<NotificationResponse> getByTaskId(
            @Parameter(description = "Task UUID") @PathVariable UUID taskId) {
        return notificationService.getByTaskId(taskId);
    }
}

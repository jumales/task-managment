package com.demo.notification.client;

import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.dto.TaskParticipantResponse;
import com.demo.common.event.TaskChangeType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/** Feign client for retrieving task data and notification templates from task-service. */
@FeignClient(name = "task-service")
public interface TaskServiceClient {

    /** Returns the active notification template for the given project and event type. */
    @GetMapping("/api/v1/projects/{projectId}/notification-templates/{eventType}")
    ProjectNotificationTemplateResponse getTemplate(@PathVariable UUID projectId,
                                                    @PathVariable TaskChangeType eventType);

    /** Returns all participants (watchers + joiners) for the given task. */
    @GetMapping("/api/v1/tasks/{taskId}/participants")
    List<TaskParticipantResponse> getParticipants(@PathVariable UUID taskId);
}

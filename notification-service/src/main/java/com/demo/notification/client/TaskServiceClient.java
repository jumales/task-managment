package com.demo.notification.client;

import com.demo.common.dto.ProjectNotificationTemplateResponse;
import com.demo.common.event.TaskChangeType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Feign client for retrieving project notification templates from task-service. */
@FeignClient(name = "task-service")
public interface TaskServiceClient {

    /** Returns the active notification template for the given project and event type. */
    @GetMapping("/api/v1/projects/{projectId}/notification-templates/{eventType}")
    ProjectNotificationTemplateResponse getTemplate(@PathVariable UUID projectId,
                                                    @PathVariable TaskChangeType eventType);
}

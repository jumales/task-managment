package com.demo.common.dto;

import com.demo.common.event.TaskChangeType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Response DTO for a project notification email template. */
@Getter
@AllArgsConstructor
public class ProjectNotificationTemplateResponse {

    private UUID id;
    private UUID projectId;

    /** The event type this template applies to. */
    private TaskChangeType eventType;

    private String subjectTemplate;
    private String bodyTemplate;
}

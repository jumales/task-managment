package com.demo.common.dto;

import lombok.Data;

/**
 * Request body for creating or replacing a project notification email template.
 * Supports placeholders: {taskId}, {taskTitle}, {projectId}, and event-specific ones
 * such as {fromStatus}, {toStatus}, {comment}, {fromPhase}, {toPhase}.
 */
@Data
public class ProjectNotificationTemplateRequest {

    /** Email subject line template. */
    private String subjectTemplate;

    /** Email body template. */
    private String bodyTemplate;
}

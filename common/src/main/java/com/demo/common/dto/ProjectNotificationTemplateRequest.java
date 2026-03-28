package com.demo.common.dto;

import lombok.Data;

/**
 * Request body for creating or replacing a project notification email template.
 * Supported placeholders are defined by {@link TemplatePlaceholder}; unknown tokens are rejected with HTTP 400.
 * Universal tokens: {taskId}, {taskTitle}, {taskUrl}, {projectId}, {userName}.
 * Event-specific tokens: {fromStatus}/{toStatus}, {comment}, {fromPhase}/{toPhase},
 * {workType}, {plannedHours}, {bookedHours}.
 */
@Data
public class ProjectNotificationTemplateRequest {

    /** Email subject line template. */
    private String subjectTemplate;

    /** Email body template. */
    private String bodyTemplate;
}

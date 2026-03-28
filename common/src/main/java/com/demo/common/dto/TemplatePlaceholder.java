package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * All supported placeholder tokens that can be used inside notification template strings.
 * Each token is referenced in templates as {@code {key}}, e.g. {@code {taskTitle}}.
 * The notification-service replaces every token with its runtime value before sending.
 */
@Getter
@AllArgsConstructor
public enum TemplatePlaceholder {

    // ── Universal (available in every event type) ──────────────────────────────

    /** Unique identifier of the task. */
    TASK_ID("taskId", "Unique identifier of the task"),

    /** Title of the task. */
    TASK_TITLE("taskTitle", "Title of the task"),

    /** Direct link to the task in the application. */
    TASK_URL("taskUrl", "Direct link to the task in the application"),

    /** Unique identifier of the project. */
    PROJECT_ID("projectId", "Unique identifier of the project"),

    /** Full name of the notification recipient. */
    USER_NAME("userName", "Full name of the notification recipient"),

    // ── STATUS_CHANGED ──────────────────────────────────────────────────────────

    /** Previous status — populated for STATUS_CHANGED events. */
    FROM_STATUS("fromStatus", "Previous task status (STATUS_CHANGED events)"),

    /** New status — populated for STATUS_CHANGED events. */
    TO_STATUS("toStatus", "New task status (STATUS_CHANGED events)"),

    // ── COMMENT_ADDED ──────────────────────────────────────────────────────────

    /** Comment content — populated for COMMENT_ADDED events. */
    COMMENT("comment", "Comment content (COMMENT_ADDED events)"),

    // ── PHASE_CHANGED ──────────────────────────────────────────────────────────

    /** Previous phase name — populated for PHASE_CHANGED events. */
    FROM_PHASE("fromPhase", "Previous phase name (PHASE_CHANGED events)"),

    /** New phase name — populated for PHASE_CHANGED events. */
    TO_PHASE("toPhase", "New phase name (PHASE_CHANGED events)"),

    // ── WORK_LOG_* ──────────────────────────────────────────────────────────────

    /** Work log type — populated for WORK_LOG_* events. */
    WORK_TYPE("workType", "Type of the work log entry (WORK_LOG_* events)"),

    /** Planned hours — populated for WORK_LOG_CREATED events. */
    PLANNED_HOURS("plannedHours", "Planned hours in the work log (WORK_LOG_CREATED events)"),

    /** Booked hours — populated for WORK_LOG_UPDATED events. */
    BOOKED_HOURS("bookedHours", "Booked hours in the work log (WORK_LOG_UPDATED events)");

    /** The token key used inside templates, e.g. {@code {taskTitle}}. */
    private final String key;

    /** Human-readable description shown to users in the placeholder catalogue. */
    private final String description;
}

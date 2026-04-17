package com.demo.task.model;

public enum OutboxEventType {
    /** Task field change (status, comment, phase) — consumed by audit-service. */
    TASK_CHANGED,
    /** Full task created — consumed by search-service. */
    TASK_CREATED,
    /** Full task updated — consumed by search-service. */
    TASK_UPDATED,
    /** Task deleted — consumed by search-service. */
    TASK_DELETED,
    /** Task archived — consumed by search-service, audit-service, reporting-service, and file-service. */
    TASK_ARCHIVED
}

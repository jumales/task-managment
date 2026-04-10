package com.demo.common.dto;

/**
 * Fixed set of phase names available in a project's workflow.
 * Using an enum prevents arbitrary strings and keeps phase names consistent across services.
 */
public enum TaskPhaseName {
    PLANNING,
    BACKLOG,
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    TESTING,
    DONE,
    RELEASED,
    REJECTED
}

package com.demo.common.dto;

import java.util.Set;

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
    REJECTED;

    /** Phases that are fully finished — all modifications are blocked. */
    public static final Set<TaskPhaseName> FINISHED_PHASES = Set.of(RELEASED, REJECTED);

    /** Phases where task fields (title, description, status, etc.) are locked. Includes DONE (dev-finished). */
    public static final Set<TaskPhaseName> FIELD_LOCKED_PHASES = Set.of(DONE, RELEASED, REJECTED);
}

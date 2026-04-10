package com.demo.common.dto;

/** Represents a distinct milestone in a task's lifecycle timeline. */
public enum TimelineState {
    PLANNED_START,
    PLANNED_END,
    REAL_START,
    REAL_END,
    /** Set automatically when a task is moved to the RELEASED phase. */
    RELEASE_DATE
}

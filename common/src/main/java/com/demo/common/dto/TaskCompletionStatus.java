package com.demo.common.dto;

/**
 * Completion state of a task based on its current phase.
 * Used as a filter parameter on list endpoints to find finished or dev-finished tasks.
 */
public enum TaskCompletionStatus {
    /** Task is in RELEASED or REJECTED phase — fully finished, all modifications blocked. */
    FINISHED,
    /** Task is in DONE phase — dev work complete, comments and booked work still allowed. */
    DEV_FINISHED
}

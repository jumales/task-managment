package com.demo.common.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Request DTO for atomically setting both planned start and end dates on a task.
 * Both fields are required; partial updates are not allowed to avoid inconsistent date ranges.
 */
@Data
public class PlannedDatesRequest {

    /** The planned start date/time for the task. Must be before plannedEnd. */
    private Instant plannedStart;

    /** The planned end date/time for the task. Must be after plannedStart. */
    private Instant plannedEnd;
}

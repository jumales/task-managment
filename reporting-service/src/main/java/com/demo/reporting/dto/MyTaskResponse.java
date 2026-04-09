package com.demo.reporting.dto;

import com.demo.common.dto.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Row in the "My Tasks" report. Includes the task id so the frontend can navigate
 * to the task detail view.
 */
@Getter
@AllArgsConstructor
public class MyTaskResponse {
    private final UUID id;
    private final String taskCode;
    private final String title;
    private final String description;
    private final TaskStatus status;
    private final Instant plannedStart;
    private final Instant plannedEnd;
    private final Instant updatedAt;
}

package com.demo.common.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class TaskRequest {
    private String title;
    private String description;
    private TaskStatus status;
    /** Optional task type classification. */
    private TaskType type;
    /** Completion percentage in the range 0–100. Defaults to 0. */
    private int progress;
    private UUID assignedUserId;
    private UUID projectId;
    /** Optional. When null, the project's default phase is assigned automatically. */
    private UUID phaseId;
    /** Required. The planned start date for the task. Must be before plannedEnd. */
    private Instant plannedStart;
    /** Required. The planned end (deadline) date for the task. Must be after plannedStart. */
    private Instant plannedEnd;
}

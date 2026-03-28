package com.demo.common.dto;

import lombok.Data;

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
}

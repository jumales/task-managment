package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TaskRequest {
    private String title;
    private String description;
    private TaskStatus status;
    private UUID assignedUserId;
    private UUID projectId;
    /** Optional. When null, the project's default phase is assigned automatically. */
    private UUID phaseId;
}

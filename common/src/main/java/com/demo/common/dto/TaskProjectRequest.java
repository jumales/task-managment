package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TaskProjectRequest {
    private String name;
    private String description;
    /** Optional prefix for auto-generated task codes (e.g. "PROJ_"). Defaults to "TASK_" when omitted. */
    private String taskCodePrefix;
    /** ID of the phase automatically assigned to new tasks when no explicit phase is provided. Must belong to this project. */
    private UUID defaultPhaseId;
}

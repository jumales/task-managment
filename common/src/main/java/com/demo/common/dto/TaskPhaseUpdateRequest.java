package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Request DTO for changing the phase of a task.
 * Used by {@code PATCH /api/v1/tasks/{id}/phase}.
 */
@Data
public class TaskPhaseUpdateRequest {

    /** The ID of the new phase to assign to the task. Must belong to the same project. */
    private UUID phaseId;
}

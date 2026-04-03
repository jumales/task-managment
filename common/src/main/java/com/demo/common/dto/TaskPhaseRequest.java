package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TaskPhaseRequest {
    private TaskPhaseName name;
    private String description;
    /** Optional user-defined display label; null clears any existing custom name. */
    private String customName;
    /** The project this phase belongs to. Required on creation. */
    private UUID projectId;
}

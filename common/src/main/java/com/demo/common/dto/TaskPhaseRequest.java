package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TaskPhaseRequest {
    private TaskPhaseName name;
    private String description;
    /** The project this phase belongs to. Required on creation. */
    private UUID projectId;
}

package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskPhaseResponse {
    private UUID id;
    private TaskPhaseName name;
    private String description;
    /** User-defined display label; null when not set — UI should fall back to the formatted enum name. */
    private String customName;
    private UUID projectId;
}

package com.demo.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class TaskPhaseRequest {
    private String name;
    private String description;
    /** The project this phase belongs to. Required on creation. */
    private UUID projectId;
    /** When true, this phase becomes the default for the project. Only one phase per project may be default. */
    @JsonProperty("isDefault")
    private boolean isDefault;
}

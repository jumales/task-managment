package com.demo.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskPhaseResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID projectId;
    @JsonProperty("isDefault")
    private boolean isDefault;
}

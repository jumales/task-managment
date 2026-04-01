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
    private UUID projectId;
}

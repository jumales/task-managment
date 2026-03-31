package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskProjectResponse {
    private UUID id;
    private String name;
    private String description;
    /** Prefix used when auto-generating task codes for this project (e.g. "PROJ_"). */
    private String taskCodePrefix;
}

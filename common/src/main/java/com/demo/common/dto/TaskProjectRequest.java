package com.demo.common.dto;

import lombok.Data;

@Data
public class TaskProjectRequest {
    private String name;
    private String description;
    /** Optional prefix for auto-generated task codes (e.g. "PROJ_"). Defaults to "TASK_" when omitted. */
    private String taskCodePrefix;
}

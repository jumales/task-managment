package com.demo.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Response for the "open tasks by project" endpoint.
 * Carries both the requesting user's open count and the project-wide total.
 */
@Getter
@AllArgsConstructor
public class ProjectTaskCountResponse {

    private final UUID projectId;
    private final String projectName;

    /** Open tasks in this project assigned to the requesting user. */
    private final long myOpenCount;

    /** Total open tasks in this project across all users. */
    private final long totalOpenCount;
}

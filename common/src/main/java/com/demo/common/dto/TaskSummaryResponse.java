package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Lightweight task representation returned by list endpoints.
 * Contains flat scalar fields only — no nested participant list — so list queries avoid
 * the N+1 participant load that {@link TaskResponse} requires for individual task fetches.
 */
@Getter
@AllArgsConstructor
public class TaskSummaryResponse {
    private UUID id;
    private String title;
    private String description;
    private TaskStatus status;
    /** Optional classification of the task (FEATURE, BUG_FIXING, etc.). */
    private TaskType type;
    /** Completion percentage in the range 0–100. */
    private int progress;
    private UUID assignedUserId;
    private String assignedUserName;
    private UUID projectId;
    private String projectName;
    private UUID phaseId;
    private String phaseName;
}

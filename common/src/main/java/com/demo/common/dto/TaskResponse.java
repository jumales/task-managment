package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskResponse {
    private UUID id;
    private String title;
    private String description;
    private TaskStatus status;
    /** Optional classification of the task (FEATURE, BUG_FIXING, etc.). */
    private TaskType type;
    /** Completion percentage in the range 0–100. */
    private int progress;
    /** All users associated with this task, each with their role (CREATOR, ASSIGNEE, VIEWER, REVIEWER). */
    private List<TaskParticipantResponse> participants;
    private TaskProjectResponse project;
    private TaskPhaseResponse phase;
}

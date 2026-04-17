package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TaskResponse {
    private UUID id;
    /** Auto-generated code combining the project prefix and a sequential number (e.g. "TASK_1"). */
    private String taskCode;
    private String title;
    private String description;
    private TaskStatus status;
    /** Optional classification of the task (FEATURE, BUG_FIXING, etc.). */
    private TaskType type;
    /** Completion percentage in the range 0–100. */
    private int progress;
    /** All users associated with this task, each with their role (CREATOR, ASSIGNEE, CONTRIBUTOR, WATCHER). */
    private List<TaskParticipantResponse> participants;
    private TaskProjectResponse project;
    private TaskPhaseResponse phase;
    /** Optimistic-lock version. Must be echoed back in PUT requests to detect concurrent modifications. */
    private Long version;
}

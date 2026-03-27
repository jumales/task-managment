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
    /** All users associated with this task, each with their role (CREATOR, ASSIGNEE, VIEWER, REVIEWER). */
    private List<TaskParticipantResponse> participants;
    private TaskProjectResponse project;
    private TaskPhaseResponse phase;
}

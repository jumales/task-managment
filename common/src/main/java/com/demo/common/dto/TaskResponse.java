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
    private UserDto assignedUser;
    private TaskProjectResponse project;
    private TaskPhaseResponse phase;
    private List<TaskCommentResponse> comments;
}

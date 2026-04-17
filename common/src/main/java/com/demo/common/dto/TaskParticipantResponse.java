package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A single user-role association on a task. */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TaskParticipantResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private TaskParticipantRole role;
}

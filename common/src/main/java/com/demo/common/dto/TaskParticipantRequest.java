package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

/** Request body for adding a participant to a task. */
@Data
public class TaskParticipantRequest {
    private UUID userId;
    private TaskParticipantRole role;
}

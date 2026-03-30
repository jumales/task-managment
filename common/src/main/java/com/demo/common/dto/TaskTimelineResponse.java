package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single timeline entry on a task. */
@Getter
@AllArgsConstructor
public class TaskTimelineResponse {
    private UUID id;
    private TimelineState state;
    private Instant timestamp;
    private UUID setByUserId;
    /** Display name resolved from user-service; null if user-service is unavailable. */
    private String setByUserName;
    private Instant createdAt;
}

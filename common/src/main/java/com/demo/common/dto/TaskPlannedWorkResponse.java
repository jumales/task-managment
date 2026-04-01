package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single planned-work entry on a task. */
@Getter
@AllArgsConstructor
public class TaskPlannedWorkResponse {
    private UUID id;
    private UUID userId;
    /** Display name resolved from user-service; null if user-service is unavailable. */
    private String userName;
    private WorkType workType;
    private BigInteger plannedHours;
    private Instant createdAt;
}

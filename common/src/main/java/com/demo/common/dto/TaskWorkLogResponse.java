package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single work log entry on a task. */
@Getter
@AllArgsConstructor
public class TaskWorkLogResponse {
    private UUID id;
    private UUID userId;
    /** Display name resolved from user-service; null if user-service is unavailable. */
    private String userName;
    private WorkType workType;
    private BigInteger plannedHours;
    private BigInteger bookedHours;
    private Instant createdAt;
}

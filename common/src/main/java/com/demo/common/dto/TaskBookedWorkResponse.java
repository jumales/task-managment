package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for a single booked-work entry on a task. */
@Getter
@AllArgsConstructor
public class TaskBookedWorkResponse {
    private UUID id;
    private UUID userId;
    /** Display name resolved from user-service; null if user-service is unavailable. */
    private String userName;
    private WorkType workType;
    private BigInteger bookedHours;
    private Instant createdAt;
}

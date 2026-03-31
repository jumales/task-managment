package com.demo.common.dto;

import lombok.Data;

import java.math.BigInteger;
import java.util.UUID;

/** Request body for creating or updating a booked-work entry on a task. */
@Data
public class TaskBookedWorkRequest {
    /** User who booked the hours. */
    private UUID userId;
    private WorkType workType;
    /** Actual hours worked and booked. Must be greater than zero. */
    private BigInteger bookedHours;
}

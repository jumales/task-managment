package com.demo.common.dto;

import lombok.Data;

import java.math.BigInteger;
import java.util.UUID;

/** Request body for creating or updating a work log entry on a task. */
@Data
public class TaskWorkLogRequest {
    private UUID userId;
    private WorkType workType;
    /** Estimated hours planned for this work entry. */
    private BigInteger plannedHours;
    /** Actual hours worked and booked against this entry. */
    private BigInteger bookedHours;
}

package com.demo.common.dto;

import lombok.Data;

import java.math.BigInteger;

/** Request body for creating or updating a booked-work entry on a task. */
@Data
public class TaskBookedWorkRequest {
    private WorkType workType;
    /** Actual hours worked and booked. Must be greater than zero. */
    private BigInteger bookedHours;
}

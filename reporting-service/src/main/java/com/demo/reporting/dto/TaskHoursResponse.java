package com.demo.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Planned vs booked hours for a single task. */
@Getter
@AllArgsConstructor
public class TaskHoursResponse {
    private final UUID taskId;
    private final String taskCode;
    private final String title;
    private final long plannedHours;
    private final long bookedHours;
}

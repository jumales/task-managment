package com.demo.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Planned vs booked hours aggregated over all tasks in a project. */
@Getter
@AllArgsConstructor
public class ProjectHoursResponse {
    private final UUID projectId;
    private final String projectName;
    private final long plannedHours;
    private final long bookedHours;
}

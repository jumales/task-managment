package com.demo.reporting.dto;

import com.demo.common.dto.WorkType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/** Planned vs booked hours for a task broken down by user and work type. */
@Getter
@AllArgsConstructor
public class DetailedHoursResponse {
    private final UUID userId;
    private final WorkType workType;
    private final long plannedHours;
    private final long bookedHours;
}

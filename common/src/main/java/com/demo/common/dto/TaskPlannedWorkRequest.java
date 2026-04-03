package com.demo.common.dto;

import lombok.Data;

import java.math.BigInteger;

/** Request body for creating a planned-work entry on a task. */
@Data
public class TaskPlannedWorkRequest {
    private WorkType workType;
    /** Estimated hours planned for this work type. */
    private BigInteger plannedHours;
}

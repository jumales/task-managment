package com.demo.common.dto;

import lombok.Data;

import java.math.BigInteger;
import java.util.UUID;

/** Request body for creating a planned-work entry on a task. */
@Data
public class TaskPlannedWorkRequest {
    /** User responsible for this planned work. */
    private UUID userId;
    private WorkType workType;
    /** Estimated hours planned for this work type. */
    private BigInteger plannedHours;
}

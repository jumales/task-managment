package com.demo.common.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Request body for setting a timeline state on a task. */
@Data
public class TaskTimelineRequest {
    /** The point in time associated with this state. */
    private Instant timestamp;
    /** The user who is recording this timeline state. */
    private UUID setByUserId;
}

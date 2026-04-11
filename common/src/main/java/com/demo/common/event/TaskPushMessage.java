package com.demo.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight WebSocket push payload sent to all STOMP clients subscribed to
 * {@code /topic/tasks/{taskId}} whenever a tracked field on that task changes.
 * Consumers use {@code changeType} to decide which sub-resource to re-fetch.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskPushMessage {

    private UUID taskId;
    private TaskChangeType changeType;
}

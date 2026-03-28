package com.demo.notification.dto;

import com.demo.common.event.TaskChangeType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Read-only view of a sent email notification. */
@Getter
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private UUID taskId;
    private UUID recipientUserId;
    private String recipientEmail;
    private TaskChangeType changeType;
    private String subject;
    private String body;
    private Instant sentAt;
}

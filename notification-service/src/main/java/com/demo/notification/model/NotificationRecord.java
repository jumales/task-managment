package com.demo.notification.model;

import com.demo.common.event.TaskChangeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Persistent record of an email notification that was successfully sent. */
@Entity
@Table(name = "notifications")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Task that triggered this notification. */
    @Column(nullable = false)
    private UUID taskId;

    /** User who received the notification. */
    @Column(nullable = false)
    private UUID recipientUserId;

    /** Email address the notification was sent to. */
    @Column(nullable = false)
    private String recipientEmail;

    /** Event type that triggered this notification. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskChangeType changeType;

    /** Email subject line. */
    @Column(nullable = false)
    private String subject;

    /** Plain-text email body. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** When the email was sent. */
    @Column(nullable = false)
    private Instant sentAt;
}

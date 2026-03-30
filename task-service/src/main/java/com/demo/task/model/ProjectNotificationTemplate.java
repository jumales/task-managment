package com.demo.task.model;

import com.demo.common.event.TaskChangeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a per-project email template for a specific task-change event type.
 * Templates support placeholders such as {taskId}, {taskTitle}, {projectId},
 * {fromStatus}, {toStatus}, {comment}, {fromPhase}, {toPhase}.
 * A project may have at most one active template per event type.
 */
@Entity
@Table(name = "project_notification_templates")
@SQLDelete(sql = "UPDATE project_notification_templates SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectNotificationTemplate {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TaskChangeType eventType;

    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}

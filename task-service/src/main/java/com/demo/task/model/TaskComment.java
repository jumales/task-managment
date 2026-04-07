package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only comment on a task.
 * Historical changes are tracked in the audit-service via Kafka events.
 */
@Entity
@Table(name = "task_comments")
@SQLDelete(sql = "UPDATE task_comments SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskComment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** User who wrote the comment; nullable for legacy rows created before this column existed. */
    private UUID userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Instant createdAt;

    private Instant deletedAt;
}

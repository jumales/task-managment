package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID taskId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Instant createdAt;

    private Instant deletedAt;
}

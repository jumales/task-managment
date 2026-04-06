package com.demo.audit.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comment_audit_records")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentAuditRecord {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    private UUID taskId;
    /** ID of the user who created the comment (not the task assignee). */
    private UUID commentCreatedByUserId;
    private UUID commentId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Instant addedAt;     // when the comment was created in task-service
    private Instant recordedAt;  // when audit-service stored this record
}

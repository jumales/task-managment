package com.demo.task.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata record for a file attached to a task.
 * The actual file bytes are stored in MinIO via file-service; this entity
 * holds only the reference ({@code fileId}) and display metadata.
 * Attachments use hard delete — there is no {@code deletedAt} column.
 */
@Entity
@Table(name = "task_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAttachment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    /** References the file record in file-service; used to build download URLs. */
    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    /** UUID of the user who uploaded this attachment. */
    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    private Instant uploadedAt;
}

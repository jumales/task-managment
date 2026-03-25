package com.demo.file.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists metadata about an uploaded file.
 * The actual binary content is stored in MinIO; this record holds the location and context.
 */
@Entity
@Table(name = "file_metadata")
@SQLDelete(sql = "UPDATE file_metadata SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    private UUID id;

    /** MinIO bucket the object is stored in (e.g. "avatars", "attachments"). */
    private String bucket;

    /** Object key inside the bucket (e.g. "a1b2c3d4-....jpg"). */
    private String objectKey;

    /** MIME type of the uploaded file (e.g. "image/jpeg"). */
    private String contentType;

    /** Original filename provided by the uploader. */
    private String originalFilename;

    /** Subject (UUID) of the JWT principal that performed the upload. */
    private String uploadedBy;

    private Instant uploadedAt;

    /** Populated by soft-delete; null while the record is active. */
    private Instant deletedAt;
}

package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a task attachment.
 * Clients can use {@code fileId} to construct a download URL:
 * {@code GET /api/v1/files/{fileId}/download}.
 */
@Getter
@AllArgsConstructor
public class TaskAttachmentResponse {

    private UUID id;

    /** References the file record in file-service; use to build download URLs. */
    private UUID fileId;

    private String fileName;
    private String contentType;

    private UUID uploadedByUserId;

    /** Display name of the uploader; null if user-service is unavailable. */
    private String uploadedByUserName;

    private Instant uploadedAt;
}

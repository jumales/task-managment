package com.demo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Returned by file-service after a successful file upload.
 * The {@code fileId} can be stored on any entity (e.g. User.avatarFileId)
 * and later resolved to a URL via {@code GET /api/v1/files/{fileId}}.
 */
@Getter
@AllArgsConstructor
public class FileUploadResponse {
    private UUID fileId;
    private String bucket;
    private String objectKey;
    private String contentType;
}

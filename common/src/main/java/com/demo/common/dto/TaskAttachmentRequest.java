package com.demo.common.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Request body for registering an uploaded file as a task attachment.
 * The file must already exist in file-service (upload it first via
 * {@code POST /api/v1/files/attachments}) before calling the task attachment endpoint.
 */
@Data
public class TaskAttachmentRequest {

    /** UUID returned by file-service after a successful upload. */
    private UUID fileId;

    /** Original filename as provided by the client (e.g. "design-mockup.png"). */
    private String fileName;

    /** MIME type of the uploaded file (e.g. "image/png"). */
    private String contentType;
}

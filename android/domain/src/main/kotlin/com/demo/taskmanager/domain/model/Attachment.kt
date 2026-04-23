package com.demo.taskmanager.domain.model

data class Attachment(
    val id: String,
    /** File-service reference; use to build download URL: GET /api/v1/files/{fileId}/download. */
    val fileId: String,
    val fileName: String,
    val contentType: String,
    val uploadedByUserId: String?,
    val uploadedByUserName: String?,
    val uploadedAt: String,
)

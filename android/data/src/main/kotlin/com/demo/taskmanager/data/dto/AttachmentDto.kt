package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentDto(
    val id: String,
    /** File-service reference; use to build download URL: GET /api/v1/files/{fileId}/download. */
    val fileId: String,
    val fileName: String,
    val contentType: String,
    val uploadedByUserId: String?,
    val uploadedByUserName: String?,
    val uploadedAt: String,
)

@Serializable
data class AttachmentCreateRequest(
    val fileId: String,
    val fileName: String,
    val contentType: String,
)

@Serializable
data class PresignedUrlDto(
    val url: String,
)

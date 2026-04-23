package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class FileUploadDto(
    val fileId: String,
    val bucket: String,
    val objectKey: String,
    val contentType: String,
)

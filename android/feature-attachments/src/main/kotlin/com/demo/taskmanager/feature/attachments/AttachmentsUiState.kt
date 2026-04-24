package com.demo.taskmanager.feature.attachments

import com.demo.taskmanager.data.dto.AttachmentDto

data class AttachmentsUiState(
    val attachments: List<AttachmentDto> = emptyList(),
    val isLoading: Boolean = false,
    /** True while a file is being uploaded to file-service and linked to the task. */
    val isUploading: Boolean = false,
    val snackbarMessage: String? = null,
)

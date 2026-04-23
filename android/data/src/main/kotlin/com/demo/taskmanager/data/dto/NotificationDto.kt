package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TaskChangeType
import kotlinx.serialization.Serializable

@Serializable
data class NotificationTemplateDto(
    val id: String,
    val projectId: String,
    /** Event type this template fires for. */
    val eventType: TaskChangeType,
    val subjectTemplate: String,
    val bodyTemplate: String,
)

@Serializable
data class NotificationTemplateRequest(
    val subjectTemplate: String,
    val bodyTemplate: String,
)

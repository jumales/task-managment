package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TaskPhaseName
import kotlinx.serialization.Serializable

@Serializable
data class PhaseDto(
    val id: String,
    val name: TaskPhaseName,
    val description: String?,
    /** User-defined display label; null means no custom name set. */
    val customName: String?,
    val projectId: String,
)

@Serializable
data class PhaseCreateRequest(
    val name: TaskPhaseName,
    val description: String? = null,
    val customName: String? = null,
    val projectId: String,
)

@Serializable
data class PhaseUpdateRequest(
    val name: TaskPhaseName,
    val description: String? = null,
    val customName: String? = null,
    val projectId: String,
)

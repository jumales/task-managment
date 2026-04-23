package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val description: String?,
    /** Prefix for auto-generated task codes (e.g. "PROJ_"). */
    val taskCodePrefix: String?,
    /** Phase auto-assigned to new tasks; null when no default is configured. */
    val defaultPhaseId: String?,
)

@Serializable
data class ProjectCreateRequest(
    val name: String,
    val description: String? = null,
    val taskCodePrefix: String? = null,
    val defaultPhaseId: String? = null,
)

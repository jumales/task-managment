package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

/** Task hit returned by GET /api/v1/search/tasks. */
@Serializable
data class TaskSearchHitDto(
    val id: String,
    val title: String?,
    val description: String?,
    val status: String?,
    val projectId: String?,
    val projectName: String?,
    val phaseId: String?,
    val phaseName: String?,
    val assignedUserId: String?,
    val assignedUserName: String?,
)

/** User hit returned by GET /api/v1/search/users. */
@Serializable
data class UserSearchHitDto(
    val id: String,
    val name: String?,
    val email: String?,
    val username: String?,
    val active: Boolean = true,
)

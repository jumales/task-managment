package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.WorkType
import kotlinx.serialization.Serializable

@Serializable
data class PlannedWorkDto(
    val id: String,
    val userId: String,
    /** Display name from user-service; null when user-service unavailable. */
    val userName: String?,
    val workType: WorkType,
    val plannedHours: Long,
    val createdAt: String,
)

@Serializable
data class WorkCreateRequest(
    val workType: WorkType,
    val plannedHours: Long? = null,
    val bookedHours: Long? = null,
)

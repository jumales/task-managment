package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.WorkType
import kotlinx.serialization.Serializable

@Serializable
data class BookedWorkDto(
    val id: String,
    val userId: String,
    /** Display name from user-service; null when user-service unavailable. */
    val userName: String?,
    val workType: WorkType,
    val bookedHours: Long,
    val createdAt: String,
)

@Serializable
data class BookedWorkCreateRequest(
    val workType: WorkType,
    val bookedHours: Long,
)

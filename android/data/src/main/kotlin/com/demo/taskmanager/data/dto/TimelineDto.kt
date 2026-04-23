package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TimelineState
import kotlinx.serialization.Serializable

@Serializable
data class TimelineDto(
    val id: String,
    val state: TimelineState,
    /** ISO-8601 timestamp for this milestone. */
    val timestamp: String,
    val setByUserId: String?,
    /** Display name from user-service; null when unavailable. */
    val setByUserName: String?,
    val createdAt: String,
)

@Serializable
data class TimelineCreateRequest(
    /** ISO-8601 timestamp. */
    val timestamp: String,
    val setByUserId: String,
)

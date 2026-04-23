package com.demo.taskmanager.domain.model

data class PlannedWork(
    val id: String,
    val userId: String,
    /** Display name from user-service; null when user-service unavailable. */
    val userName: String?,
    val workType: WorkType,
    val plannedHours: Long,
    val createdAt: String,
)

package com.demo.taskmanager.domain.model

/** Lightweight task row for list views. */
data class TaskSummary(
    val id: String,
    val taskCode: String,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val type: TaskType?,
    val progress: Int,
    val assignedUserId: String?,
    val assignedUserName: String?,
    val projectId: String?,
    val projectName: String?,
    val phaseId: String?,
    val phaseName: String?,
)

/** Task detail view including project and phase. */
data class Task(
    val id: String,
    val taskCode: String,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val type: TaskType?,
    val progress: Int,
    val project: Project?,
    val phase: Phase?,
    /** Optimistic-lock version — echo back in PUT requests. */
    val version: Long,
)

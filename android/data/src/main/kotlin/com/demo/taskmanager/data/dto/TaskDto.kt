package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TaskType
import kotlinx.serialization.Serializable

/** Lightweight task row returned by list endpoints (no nested participant list). */
@Serializable
data class TaskSummaryDto(
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

/** Task detail view including participants, project, and phase. */
@Serializable
data class TaskDto(
    val id: String,
    val taskCode: String,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val type: TaskType?,
    val progress: Int,
    val participants: List<ParticipantDto>,
    val project: ProjectDto?,
    val phase: PhaseDto?,
    /** Optimistic-lock version — echo back in PUT requests. */
    val version: Long,
)

/** Full task view including timelines, planned work, booked work, and assigned user profile. */
@Serializable
data class TaskFullDto(
    val id: String,
    val taskCode: String,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val type: TaskType?,
    val progress: Int,
    val participants: List<ParticipantDto>,
    val project: ProjectDto?,
    val phase: PhaseDto?,
    val assignedUser: UserDto?,
    val timelines: List<TimelineDto>,
    val plannedWork: List<PlannedWorkDto>,
    val bookedWork: List<BookedWorkDto>,
    val version: Long,
)

@Serializable
data class TaskCreateRequest(
    val title: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val type: TaskType? = null,
    val progress: Int = 0,
    val assignedUserId: String? = null,
    val projectId: String,
    val phaseId: String? = null,
    /** ISO-8601 planned start timestamp. */
    val plannedStart: String? = null,
    /** ISO-8601 planned end timestamp. */
    val plannedEnd: String? = null,
)

@Serializable
data class TaskUpdateRequest(
    /** Optimistic-lock version from the last GET response. */
    val version: Long,
    val title: String? = null,
    val description: String? = null,
    val status: TaskStatus? = null,
    val type: TaskType? = null,
    val progress: Int? = null,
    val assignedUserId: String? = null,
    val projectId: String? = null,
    val phaseId: String? = null,
    val plannedStart: String? = null,
    val plannedEnd: String? = null,
)

@Serializable
data class PlannedDatesRequest(
    /** ISO-8601 planned start; must be before plannedEnd. */
    val plannedStart: String,
    /** ISO-8601 planned end; must be after plannedStart. */
    val plannedEnd: String,
)

@Serializable
data class TaskPhaseUpdateRequest(
    val phaseId: String,
)

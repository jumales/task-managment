package com.demo.taskmanager.data.dto

import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.WorkType
import kotlinx.serialization.Serializable

@Serializable
data class MyTaskReportDto(
    val id: String,
    val taskCode: String,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val phaseName: String,
    val plannedStart: String?,
    val plannedEnd: String?,
    val updatedAt: String?,
)

@Serializable
data class HoursByTaskDto(
    val taskId: String,
    val taskCode: String,
    val title: String,
    val plannedHours: Long,
    val bookedHours: Long,
)

@Serializable
data class HoursByProjectDto(
    val projectId: String,
    val projectName: String,
    val plannedHours: Long,
    val bookedHours: Long,
)

@Serializable
data class HoursDetailedDto(
    val userId: String,
    val workType: WorkType,
    val plannedHours: Long,
    val bookedHours: Long,
)

@Serializable
data class ProjectOpenTaskCountDto(
    val projectId: String,
    val projectName: String,
    val myOpenCount: Long,
    val totalOpenCount: Long,
)

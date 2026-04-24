package com.demo.taskmanager.feature.tasks.list

import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.domain.model.TaskStatus

/** Immutable snapshot of all active list filters; drives PagingSource recreation on change. */
data class TasksFilterState(
    val status: TaskStatus? = null,
    val completionStatus: TaskCompletionStatus? = null,
    val projectId: String? = null,
    val assignedUserId: String? = null,
)

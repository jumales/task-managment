package com.demo.taskmanager.domain.usecase

import com.demo.taskmanager.domain.model.Task
import com.demo.taskmanager.domain.model.TaskStatus
import com.demo.taskmanager.domain.model.TaskSummary

/** Returns a page of task summaries matching optional filters. */
interface GetTasksUseCase {
    suspend operator fun invoke(
        page: Int = 0,
        size: Int = 20,
        projectId: String? = null,
        userId: String? = null,
        status: TaskStatus? = null,
    ): List<TaskSummary>
}

/** Returns task detail by ID. */
fun interface GetTaskUseCase {
    suspend operator fun invoke(id: String): Task
}

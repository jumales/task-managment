package com.demo.taskmanager.feature.tasks.detail

import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.domain.model.Comment

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState
    data class Loaded(val task: TaskFullDto, val comments: List<Comment>) : TaskDetailUiState
    data class Error(val throwable: Throwable) : TaskDetailUiState
}

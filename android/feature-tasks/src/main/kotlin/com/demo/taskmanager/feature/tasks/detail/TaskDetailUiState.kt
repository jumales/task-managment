package com.demo.taskmanager.feature.tasks.detail

import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.domain.model.Comment

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState
    data class Loaded(
        val task: TaskFullDto,
        val comments: List<Comment>,
        /** Non-null while a snackbar should be shown; cleared after display. */
        val snackbarMessage: String? = null,
        /** True while an addComment call is in flight. */
        val isSubmittingComment: Boolean = false,
    ) : TaskDetailUiState
    data class Error(val throwable: Throwable) : TaskDetailUiState
}

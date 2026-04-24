package com.demo.taskmanager.feature.tasks.create

/** UI state for the task create and edit flows. */
sealed interface TaskCreateUiState {
    data object Idle : TaskCreateUiState
    data object Submitting : TaskCreateUiState
    data class Success(val taskId: String) : TaskCreateUiState
    data class Error(val message: String) : TaskCreateUiState
}

/** UI state for the task edit screen (includes initial load phase). */
sealed interface TaskEditUiState {
    data object Loading : TaskEditUiState
    /** Task is in DONE/RELEASED/REJECTED — form fields are locked. */
    data object Blocked : TaskEditUiState
    data class Ready(val snackbarMessage: String? = null) : TaskEditUiState
    data object Submitting : TaskEditUiState
    data class Success(val taskId: String) : TaskEditUiState
}

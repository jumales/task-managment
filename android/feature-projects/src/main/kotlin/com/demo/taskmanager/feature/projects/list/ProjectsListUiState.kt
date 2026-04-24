package com.demo.taskmanager.feature.projects.list

data class ProjectsListUiState(
    val isAdmin: Boolean = false,
    val snackbarMessage: String? = null,
    val showCreateDialog: Boolean = false,
)

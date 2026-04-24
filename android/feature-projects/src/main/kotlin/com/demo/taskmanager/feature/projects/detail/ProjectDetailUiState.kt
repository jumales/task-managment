package com.demo.taskmanager.feature.projects.detail

import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.ProjectDto

sealed interface ProjectDetailUiState {
    data object Loading : ProjectDetailUiState

    data class Loaded(
        val project: ProjectDto,
        val phases: List<PhaseDto>,
        val isAdmin: Boolean,
        val snackbarMessage: String? = null,
    ) : ProjectDetailUiState

    data class Error(val throwable: Throwable) : ProjectDetailUiState
}

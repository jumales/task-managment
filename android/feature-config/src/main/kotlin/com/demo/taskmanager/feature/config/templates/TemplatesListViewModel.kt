package com.demo.taskmanager.feature.config.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.NotificationTemplateDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.enums.TaskChangeType
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplatesListUiState(
    val projects: List<ProjectDto> = emptyList(),
    val selectedProject: ProjectDto? = null,
    /** Templates keyed by event type; null value means no custom template (uses default). */
    val templatesByType: Map<TaskChangeType, NotificationTemplateDto> = emptyMap(),
    val isLoadingProjects: Boolean = true,
    val isLoadingTemplates: Boolean = false,
    val snackbarMessage: String? = null,
)

/**
 * Loads the project list and per-project notification templates.
 * Fetches up to 50 projects on init (sufficient for an admin config screen).
 */
@HiltViewModel
class TemplatesListViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesListUiState())
    val uiState: StateFlow<TemplatesListUiState> = _uiState.asStateFlow()

    init { loadProjects() }

    private fun loadProjects() {
        viewModelScope.launch {
            when (val r = repository.getProjects(page = 0, size = 50)) {
                is NetworkResult.Success   -> _uiState.update { it.copy(projects = r.data.content, isLoadingProjects = false) }
                is NetworkResult.Error     -> _uiState.update { it.copy(isLoadingProjects = false, snackbarMessage = r.error?.message ?: "Failed to load projects") }
                is NetworkResult.Exception -> _uiState.update { it.copy(isLoadingProjects = false, snackbarMessage = r.throwable.localizedMessage ?: "Network error") }
            }
        }
    }

    /** Selects a project and loads its configured notification templates. */
    fun selectProject(project: ProjectDto) {
        _uiState.update { it.copy(selectedProject = project, isLoadingTemplates = true) }
        viewModelScope.launch {
            when (val r = repository.getNotificationTemplates(project.id)) {
                is NetworkResult.Success   -> {
                    val byType = r.data.associateBy { it.eventType }
                    _uiState.update { it.copy(templatesByType = byType, isLoadingTemplates = false) }
                }
                is NetworkResult.Error     -> _uiState.update { it.copy(isLoadingTemplates = false, snackbarMessage = r.error?.message ?: "Failed to load templates") }
                is NetworkResult.Exception -> _uiState.update { it.copy(isLoadingTemplates = false, snackbarMessage = r.throwable.localizedMessage ?: "Network error") }
            }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
